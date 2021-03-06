
package crosswords.mine.wiki

import java.io._
import crosswords.util.{Progress, Parallel, Packer}
import org.apache.commons.compress.compressors.CompressorStreamFactory
import play.api.libs.json.{Json, JsObject}

/**
 * Convert Wiktionary dump file into JSON definitions.
 *
 * @author Johan Berdat
 * @author Laurent Valette
 * @author Vincent Mettraux
 */
object Wiktionary {

  // Clean title
  private def clean(title: String) =
    title.toLowerCase.filter(c => c >= 'a' && c <= 'z').trim

  // Clean reference
  private def clean(ref: Reference) = {
    var result = ref.link
    if (result.contains("//"))
      result = ""
    val colon = result.lastIndexOf(':')
    if (colon >= 0)
      result = result.substring(colon + 1)
    val hash = result.indexOf('#')
    if (hash >= 0)
      result = result.substring(0, hash)
    result
  }

  // Abbreviations, alternate forms, alternate spelling, symbols...
  private def isEquivalent(title: String): Boolean = {
    val t = clean(title)
    if (t.contains("abbreviation"))
      return true
    if (t.contains("alternat") && (t.contains("form") || t.contains("spell")))
      return true
    if (t.contains("symbol"))
      return true
    false
  }

  // Hyperonyms, hyponyms, holonyms, synonyms, antonyms,
  // related terms/forms, derived terms, see also,
  // homographs, homonyms, homophones, anagrams,
  // etymology...
  private def isAssociated(title: String): Boolean = {
    val t = clean(title)
    if (t.contains("ym") && (t.contains("hyper") || t.contains("hypo") || t.contains("holo") || t.contains("sy") ||
        t.contains("anto") || t.contains("acro") || t.contains("paro")))
      return true
    if (t.contains("rel") && (t.contains("term") || t.contains("form") || t.contains("char")))
      return true
    if (t.contains("der") && t.contains("ed"))
      return true
    if (t == "seealso")
      return true
    if (t.contains("homo"))
      return true
    if (t.contains("anagram"))
      return true
    if (t.contains("et") && t.contains("gy"))
      return true
    false
  }

  // Adjective, verb, adverb, noun, pronouns...
  private def isDefinition(title: String): Boolean = {
    val t = clean(title)
    if (t.contains("adjectiv") || t.contains("verb") || t.contains("parti") || t.contains("noun"))
      return true
    if (t.contains("pre") && t.contains("sition"))
      return true
    // TODO others
    false
  }

  // Get all headers, according to predicate
  private def headers(root: Markup, predicate: String => Boolean): Seq[Header] =
    Helper.headers(root).filter(h => predicate(Helper.toString(h.title)))

  // Get all references, in specified headers
  private def references(headers: Seq[Header]): Seq[String] =
    headers.flatMap(h => Helper.references(h)).map(clean).distinct.filter(_.nonEmpty).sorted

  /**
   * Generate JSON structures from specified page.
   */
  def extract(title: String, page: String): Seq[JsObject] = {

    // Get english section
    val markup = Markup(page)
    for (english <- headers(markup, _ == "English").filter(_.lvl == 2)) yield {

      // Get interesting references
      val equivalents = references(headers(english, isEquivalent)).
        filter(w => w.toLowerCase != title.toLowerCase)
      val associated = references(headers(english, isAssociated)).
        filter(w => !equivalents.contains(w) && w.toLowerCase != title.toLowerCase)
      val other = references(Seq(english)).
        filter(w => !equivalents.contains(w) && !associated.contains(w) && w.toLowerCase != title.toLowerCase)
      // TODO expand noun/verb/... inline macros for word variations (-> equivalents)

      // Extract definitions
      val definitions = Helper.macroBlocks(english).filter(_.mac.name == "trans-top").map(_.mac.params.head._2).toVector
      val examples = Seq.empty[String]
      // TODO extract definitions, quotations and examples sentences

      // Create JSON object
      var obj = Json.obj("word" -> title)
      if (equivalents.nonEmpty)
        obj += "equivalents" -> Json.toJson(equivalents)
      if (associated.nonEmpty)
        obj += "associated" -> Json.toJson(associated)
      if (other.nonEmpty)
        obj += "other" -> Json.toJson(other)
      if (definitions.nonEmpty)
        obj += "definitions" -> Json.toJson(definitions)
      if (examples.nonEmpty)
        obj += "examples" -> Json.toJson(examples)
      obj

    }

  }

  def main(args: Array[String]) {

    // Local file path
    val path = "../data/enwiktionary-latest-pages-articles.xml.bz2"
    val count = 576160 // all pages: 3996006

    // Get (uncompressed) input stream
    var input: InputStream = new BufferedInputStream(new FileInputStream(path))
    if (path.endsWith(".bz2"))
      input = new CompressorStreamFactory().createCompressorInputStream(input)

    // Iterate and write on disk
    val progress = new Progress(count)
    for ((it, i) <- Parallel.split(new Pages(input)).zipWithIndex.par) {
      // TODO improve size of packs
      for ((objs, j) <- it.flatMap(p => extract(p._1, p._2)).grouped(1000).zipWithIndex) {
        Packer.writeBZ2("../data/definitions/wiktionary_" + i + "_" + j + ".json.bz2", Packer.pack(objs))
        progress.advance(objs.size)
        println(progress)
      }
      println("=> core #" + i + " finished")
    }

    // Close resources
    input.close()

  }

}

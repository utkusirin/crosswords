
package crosswords.db

import java.sql.{SQLException, DriverManager, Connection}

/**
 * Helpers for database manipulation.
 *
 * @author Utku Sirin
 * @author Johan Berdat
 */
object Helper {

  val DEFAULT_URL = "jdbc:mysql://localhost:3306/?user=root&password=Root2015"
  val DEFAULT_USER = "root"
  val DEFAULT_PASSWORD = "Root2015"

  /**
   * Create a new connection using specified url and credentials.
   */
  def open(url: String, user: String, password: String, table: String = ""): Connection =
    DriverManager.getConnection(url + table + "?user=" + user + "&password=" + password)

  /**
   * Create a new connection using default url and credentials.
   */
  def open(table: String = ""): Connection =
    open(DEFAULT_URL, DEFAULT_URL, DEFAULT_PASSWORD, table)

  /**
   * Safely close specified connection.
   */
  def close(connection: Connection) {
    try connection.close() catch { case _: SQLException => /* empty on purpose */ }
  }

  /**
   * Safe block for connection usage.
   * The connection is closed at the end.
   */
  def using[A](connection: Connection)(code: Connection => A): A =
    try code(connection) finally close(connection)

  /**
   * Execute a SQL update.
   */
  def update(sql: String)(implicit connection: Connection): Int = {
    val statement = connection.prepareStatement(sql)
    statement.executeUpdate()
  }

}

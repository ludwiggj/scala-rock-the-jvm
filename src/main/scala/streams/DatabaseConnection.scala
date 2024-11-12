package streams

import cats.effect.IO

case class DatabaseConnection(connection: String)

object DatabaseConnection {
  val acquired: String = "Acquired"
  val released: String = "Released"

  def acquire: IO[DatabaseConnection] = IO {
    val conn: DatabaseConnection = DatabaseConnection("jlaConnection")
    println(s"Acquiring connection to the database: $conn")
    conn
  }

  def release: DatabaseConnection => IO[Unit] = (conn: DatabaseConnection) =>
    IO.println(s"Releasing connection to the database: $conn")
}
package filewriter

import cats.effect._
import cats.effect.unsafe.implicits.global
import doobie._
import doobie.implicits._
import fs2.io.file.Files
import fs2.text

import java.nio.file.{FileSystems, Path}

object QueryToFile {
  def main(args: Array[String]): Unit = {
    val xa: Transactor[IO] = Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",
      "jdbc:postgresql:myimdb",
      "postgres", // username
      "example"   // password
    )

    val actorsNamesStream: fs2.Stream[doobie.ConnectionIO, String] =
      sql"select name from actors".query[String].stream

    val filePathIO: IO[Path] = IO(FileSystems.getDefault().getPath(".", "actors.txt"))

    // Query taken from https://stackoverflow.com/questions/60569610/save-doobie-stream-from-database-to-file
    val program = for {
      filePath <- filePathIO
      _        <- IO.println(s"Existing file deleted? ${filePath.toFile.delete()}")
      _        <- actorsNamesStream
        .transact(xa)
        .intersperse("\n")
        .through(text.utf8Encode[IO])
        .through(Files[IO].writeAll(filePath))
        .compile
        .drain
    } yield ()

    program.unsafeRunSync()

    // Want to split the fs2 stream output to two files?
    // See https://stackoverflow.com/questions/64188023/splitting-the-fs2-stream-output-to-two-files
  }
}
package filewriter

import cats.effect._
import cats.effect.unsafe.implicits.global
import domain.Movie
import doobie._
import doobie.implicits._
import fs2.io.file.Files
import fs2.text
import java.nio.file.{FileSystems, Path}

// Very important to deal with arrays
import doobie.postgres._
import doobie.postgres.implicits._

object AnotherQueryToFile {
  def main(args: Array[String]): Unit = {
    val xa: Transactor[IO] = Transactor.fromDriverManager[IO](
      "org.postgresql.Driver",
      "jdbc:postgresql:myimdb",
      "postgres", // username
      "example"   // password
    )

    val findAllMovies: fs2.Stream[doobie.ConnectionIO, Movie] = {
      sql"""
                       |SELECT m.id,
                       |       m.title,
                       |       m.year_of_production,
                       |       array_agg(a.name) as actors,
                       |       d.name || ' ' || d.last_name
                       |FROM movies m
                       |JOIN movies_actors ma ON m.id = ma.movie_id
                       |JOIN actors a ON ma.actor_id = a.id
                       |JOIN directors d ON m.director_id = d.id
                       |GROUP BY (m.id,
                       |          m.title,
                       |          m.year_of_production,
                       |          d.name,
                       |          d.last_name)
                       |""".stripMargin
        .query[Movie].stream
    }

    val filePathIO: IO[Path] = IO(FileSystems.getDefault.getPath(".", "actors2.txt"))

    val program = for {
      filePath <- filePathIO
      _        <- IO.println(s"Existing file deleted? ${filePath.toFile.delete()}")
      _        <- findAllMovies
        .transact(xa)
        // Explode list of actors
        .map(m => m.actors.map(a => s"Actor: $a, Film: ${m.title} (${m.year}), Director: ${m.director}"))
        // Flatten into a single stream
        .flatMap(seq => fs2.Stream.emits(seq))
        .intersperse("\n")
        .through(text.utf8Encode[IO])
        .through(Files[IO].writeAll(filePath))
        .compile
        .drain
    } yield ()

    program.unsafeRunSync()
  }
}
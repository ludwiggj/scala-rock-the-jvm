package tagless

import cats.effect._
import cats.implicits._
import doobie._
import doobie.hikari.HikariTransactor

object TaglessDoobieApp extends IOApp {
  val postgres: Resource[IO, HikariTransactor[IO]] = for {
    ce <- ExecutionContexts.fixedThreadPool[IO](32)
    xa <- HikariTransactor.newHikariTransactor[IO] (
      "org.postgresql.Driver",
      "jdbc:postgresql:myimdb",
      "postgres", // username
      "example", // password
      ce
    )
  } yield xa

  val directors: Directors[IO] = Directors.make(postgres)

  val program: IO[Unit] = for {
    id <- directors.create("Steven", "Spielberg")
    spielberg <- directors.findById(id)
    _ <- IO.println(s"The director of Jurassic Park is: $spielberg")
  } yield ()

  override def run(args: List[String]): IO[ExitCode] =
    program.as(ExitCode.Success)
}

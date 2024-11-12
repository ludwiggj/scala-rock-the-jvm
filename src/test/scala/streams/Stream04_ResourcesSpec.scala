package streams

import cats.effect.IO
import cats.implicits.catsSyntaxOptionId
import fs2.Stream
import munit.CatsEffectSuite
import streams.ActorRepository.MyException
import streams.Fixture._

class Stream04_ResourcesSpec extends CatsEffectSuite {
  test("resource not released if stream contains unhandled error") {
    var dbConnectionStateIO: IO[Option[String]] = IO(Option.empty)

    def acquire: IO[DatabaseConnection] =
      IO {
        dbConnectionStateIO = IO(DatabaseConnection.acquired.some)
      } >>
        DatabaseConnection.acquire

    def release: DatabaseConnection => IO[Unit] = (conn: DatabaseConnection) =>
      IO {
        dbConnectionStateIO = IO(DatabaseConnection.released.some)
      } >>
        DatabaseConnection.release(conn)

    interceptIO[MyException] {
      acquire.flatMap { conn =>
        for {
          _ <- spiderMenActorsStream.evalMap(actor => ActorRepository.save(conn, actor)).compile.drain
          _ <- release(conn)
        } yield ()
      }
    } >>
      assertIO(dbConnectionStateIO, DatabaseConnection.acquired.some)
  }

  test("resource released if stream error is handled") {
    var dbConnectionStateIO: IO[Option[String]] = IO(Option.empty)

    def acquire: IO[DatabaseConnection] =
      IO {
        dbConnectionStateIO = IO(DatabaseConnection.acquired.some)
      } >>
        DatabaseConnection.acquire

    def release: DatabaseConnection => IO[Unit] = (conn: DatabaseConnection) =>
      IO {
        dbConnectionStateIO = IO(DatabaseConnection.released.some)
      } >>
        DatabaseConnection.release(conn)

    val saveActorsWithRecovery: IO[List[Any]] = acquire.flatMap { conn =>
      val savedActorStream: Stream[IO, Any] =
        for {
          savedActor <- spiderMenActorsStream.evalMap(actor => ActorRepository.save(conn, actor)).handleErrorWith {
            error =>
              for {
                errorStream <- Stream.eval(IO(s"handleErrorWith Error: ${error.getMessage}"))
                _ <- Stream.eval(release(conn))
              } yield errorStream
          }
        } yield savedActor
     savedActorStream.compile.toList
    }

    assertIO(
      saveActorsWithRecovery,
      List(
        13, 14,
        "handleErrorWith Error: Cannot save actor Actor(15,Andrew,Garfield,Spider) - something went wrong during the communication with the persistence layer"
      )
    ) >>
      assertIO(dbConnectionStateIO, DatabaseConnection.released.some)
  }

  // We don’t have to use the handleErrorWith method to manage the release of resources used by the stream.
  // Instead, the fs2 library implements the bracket pattern to manage resources.
  //
  //The bracket pattern defines two functions: The first is used to acquire a resource; The second is
  // guaranteed to be called when the resource is no longer needed.
  //
  // The fs2 library implements the bracket pattern through the bracket method:
  //
  // fs2 library code
  // def bracket[F[x] >: Pure[x], R](acquire: F[R])(release: R => F[Unit]): Stream[F, R] = ???

  // The function acquire is used to acquire a resource, and the function release is used to release
  // the resource. The resulting stream has elements of type R. Moreover, the resource is released
  // at the end, both in case of success and in case of error. Notice that both the acquire and
  // release functions returns an effect since they can throw exceptions during the acquisition or
  // release of resources.

  // Use a resource during the persistence of the stream containing the actors of the JLA.

  // First, we define a value class representing a connection to a database (DatabaseConnection)
  // We use the DatabaseConnection as the resource we want to acquire and release through
  // the bracket pattern.
  test("resource acquired and released using a bracket is released even if there is an unhandled error") {
    var dbConnectionStateIO: IO[Option[String]] = IO(Option.empty)

    def acquire: IO[DatabaseConnection] =
      IO {
        dbConnectionStateIO = IO(DatabaseConnection.acquired.some)
      } >>
        DatabaseConnection.acquire

    def release: DatabaseConnection => IO[Unit] = (conn: DatabaseConnection) =>
      IO {
        dbConnectionStateIO = IO(DatabaseConnection.released.some)
      } >>
        DatabaseConnection.release(conn)

    // We call the bracket method and save the actors in the stream
    // Since the saving of the actors throws an exception when the stream is evaluated, the stream will terminate
    // with an error. The release function is called, and the connection is released
    interceptIO[MyException] {
      Stream.bracket(acquire)(release).flatMap(
        conn => spiderMenActorsStream.evalMap(actor => ActorRepository.save(conn, actor))
      ).compile.toList
    } >>
      assertIO(dbConnectionStateIO, DatabaseConnection.released.some)
  }

  test("resource acquired and released using a bracket is released if there is a handled error") {
    var dbConnectionStateIO: IO[Option[String]] = IO(Option.empty)

    def acquire: IO[DatabaseConnection] =
      IO {
        dbConnectionStateIO = IO(DatabaseConnection.acquired.some)
      } >>
        DatabaseConnection.acquire

    def release: DatabaseConnection => IO[Unit] = (conn: DatabaseConnection) =>
      IO {
        dbConnectionStateIO = IO(DatabaseConnection.released.some)
      } >>
        DatabaseConnection.release(conn)

    val saveActorsWithRecovery: IO[List[Any]] =
      Stream.bracket(acquire)(release).flatMap(
        conn => spiderMenActorsStream.evalMap(actor => ActorRepository.save(conn, actor)).handleErrorWith {
          error => Stream.eval(IO(s"handleErrorWith Error: ${error.getMessage}"))
        }
      ).compile.toList

    assertIO(
      saveActorsWithRecovery,
      List(
        13, 14,
        "handleErrorWith Error: Cannot save actor Actor(15,Andrew,Garfield,Spider) - something went wrong during the communication with the persistence layer"
      )
    ) >>
      assertIO(dbConnectionStateIO, DatabaseConnection.released.some)
  }
}
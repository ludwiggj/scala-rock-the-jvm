package streams

import cats.effect.IO
import fs2.Stream
import munit.CatsEffectSuite
import streams.Fixture._

class Stream03_ErrorHandlingSpec extends CatsEffectSuite {
  // If the effect fails, the returned stream fails.
  // What if pulling a value from a stream fails with an exception?
  // Let’s introduce a repository persisting an Actor
  import streams.ActorRepository.MyException

  test("stream throws exception and stops") {
    interceptIO[MyException](justiceLeagueActorsStream.evalMap(ActorRepository.save).compile.drain)
  }

  // handleErrorWith method handles an error by returning a new stream
  // fs2 library code
  // def handleErrorWith[F2[x] >: F[x], O2 >: O](h: Throwable => Stream[F2, O2]): Stream[F2, O2] = ???
  // The O2 type must be a supertype of O’s original type (Int). Since both Int and Unit are subtypes of AnyVal,
  // we can use the AnyVal type (the least common supertype) to represent the resulting stream.
  test("handle stream error with handleErrorWith") {
    val savedJusticeLeagueActors: Stream[IO, Int] = justiceLeagueActorsStream.evalMap(ActorRepository.save)
    def errorHandler: Throwable => Stream[IO, String] = error => Stream.eval(IO(s"handleErrorWith Error: ${error.getMessage}"))
    val errorHandledSavedJusticeLeagueActors: Stream[IO, Any] = savedJusticeLeagueActors.handleErrorWith(errorHandler)

    assertIO(
      errorHandledSavedJusticeLeagueActors.compile.toList,
      List(
        0, 1, 2,
        "handleErrorWith Error: Cannot save actor Actor(3,Ben,Fisher,Justice Leaguer) - something went wrong during the communication with the persistence layer"
      )
    )
  }

  // The attempt method works using the scala.Either type, which returns a stream of Either elements. The
  // resulting stream pulls elements wrapped in a Right instance until the first error occurs, which is
  // represented as an instance of a Throwable wrapped in a Left
  test("handle stream error with attempt") {
    val savedJusticeLeagueActors: Stream[IO, Int] = justiceLeagueActorsStream.evalMap(ActorRepository.save)

    val attemptedSavedActors: Stream[IO, Either[Throwable, Int]] = savedJusticeLeagueActors.attempt

    val processedStream: IO[List[String]] = attemptedSavedActors.evalMap {
      case Left(error) => IO.println(s"Error saving actor with id: $error") >> IO("error")
      case Right(id) => IO.println(s"Saved actor with id: $id") >> IO(id.toString)
    }.compile.toList

    assertIO(processedStream, List("0", "1", "2", "error"))
  }

  // attempt method is implemented internally using the handleErrorWith

  // fs2 library code
  // def attempt: Stream[F, Either[Throwable, O]] =
  //   map(Right(_): Either[Throwable, O])                // Stream[F, Either[Throwable, O]]
  //     .handleErrorWith(e => Stream.emit(Left(e)))
}
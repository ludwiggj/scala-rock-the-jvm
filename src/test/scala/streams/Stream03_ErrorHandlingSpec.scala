package streams

import cats.effect.IO
import munit.CatsEffectSuite
import streams.Fixture._
import scala.util.Random

class Stream03_ErrorHandlingSpec extends CatsEffectSuite {
  // If the effect fails, the returned stream fails.

  // What if pulling a value from a stream fails with an exception?
  case class MyException(msg: String) extends RuntimeException

  // Let’s introduce a repository persisting an Actor
  object ActorRepository {
    def save(actor: Actor): IO[Int] = IO {
      println(s"Saving actor: $actor")
      if (Random.nextInt() % 2 == 0) {
        val exceptionMsg = s"Cannot save actor $actor - something went wrong during the communication with the persistence layer"
        println(exceptionMsg)
        throw MyException(exceptionMsg)
      }
      println(s"Saved actor $actor")
      actor.id
    }
  }

  test("stream throws exception and stops") {
    interceptIO[MyException](justiceLeagueActorsStream.evalMap(ActorRepository.save).compile.drain)
  }
}
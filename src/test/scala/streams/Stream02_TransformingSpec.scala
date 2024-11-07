package streams

import cats.effect.IO
import fs2.{Pipe, Pure, Stream}
import munit.CatsEffectSuite
import streams.Fixture._

import scala.collection.SortedMap

class Stream02_TransformingSpec extends CatsEffectSuite {
  test("concatenate two streams") {
    val heroes: Stream[Pure, Actor] = avengerActorsStream ++ spiderMenActorsStream
    assertEquals(heroes.toList, avengerActors ++ spiderMenActors)
  }

  // The Stream type forms a monad on the O type parameter, which means that a flatMap method
  // is available on streams, and we can use it to concatenate operations concerning the
  // output values of the stream.

  // For example, printing to the console the elements of a stream uses the flatMap method
  //  def flatMap[F2[x] >: F[x], O2](f: O => Stream[F2, O2]): Stream[F2, O2]
  test("flatMap and eval a pure stream into an effectful stream") {
    val effectfulJusticeLeagueActors: Stream[IO, Unit] = justiceLeagueActorsStream.flatMap { actor =>
      Stream.eval(IO.println(actor))
    }
    assertIO(effectfulJusticeLeagueActors.compile.drain, ())
  }

  // The pattern of calling the function Stream.eval inside a flatMap is so common that fs2
  // provides a shortcut for it through the evalMap method

  // def evalMap[F2[x] >: F[x], O2](f: O => F2[O2]): Stream[F2, O2]
  test("evalMap a pure stream into an effectful stream") {
    val effectfulSpidermenActors: Stream[IO, Unit] = spiderMenActorsStream.evalMap(IO.println)
    assertIO(effectfulSpidermenActors.compile.drain, ())
  }

  // If we need to perform some effects on the stream, but we don’t want to change the type
  // of the stream, we can use the evalTap method:
  test("evalTap evaluates effect without changing stream return type") {
    val evalTappedSpidermenActors: Stream[IO, Actor] = spiderMenActorsStream.evalTap(IO.println)
    assertIO(evalTappedSpidermenActors.compile.drain, ())
  }

  // An essential feature of fs2 streams is that their functions take constant time, regardless
  // of the structure of the stream itself. So, concatenating two streams is a constant time
  // operation, whether the streams contain many elements or are infinite. As we will see in
  // the rest of the article, this feature concerns the internal representation, which is
  // implemented as a pull-based structure.

  // group Avengers by actor's name - fold works as per usual
  test("group actors by first name") {
    val emptyActorMap: Map[String, List[Actor]] = Map.empty
    val avengersActorsByFirstName: Stream[Pure, Map[String, List[Actor]]] = avengerActorsStream.fold(emptyActorMap) {
      case (map, actor) => map + (actor.firstName -> (actor :: map.getOrElse(actor.firstName, Nil)))
    }

    assertEquals(SortedMap.from(avengersActorsByFirstName.toList.head).toMap, Map(
      "Chris" -> List(chrisHemsworth, chrisEvans),
      "Jeremy" -> List(jeremyRenner),
      "Mark" -> List(markRuffalo),
      "Robert" -> List(robertDowneyJr),
      "Scarlett" -> List(scarlettJohansson),
      "Tom" -> List(tomHolland)
    ))
  }

  // Many other streaming libraries define streams and transformation in terms of sources, pipes, and sinks.
  // A source generates the elements of a stream, then transformed through a sequence of stages or pipes,
  // and finally consumed by a sink. For example, the Akka Stream library has specific types modeling these
  // concepts.

  // In fs2, the only available type modeling the above streaming concepts is Pipe[F[_], -I, +O]. Pipe is a
  // type alias for the function Stream[F, I] => Stream[F, O]. So, a Pipe[F[_], I, O] represents nothing more
  // than a function between two streams, the first emitting elements of type I, and the second emitting
  // elements of type O.

  // Therefore we can use pipes to represent transformations on streams as a sequence of stages. The through
  // method applies a pipe to a stream. Its internal implementation is straightforward since it applies the
  // function defined by the pipe to the stream:

  // fs2 library code
  // def through[F2[x] >: F[x], O2](f: Stream[F, O] => Stream[F2, O2]): Stream[F2, O2] = f(this)

  test("transform actors into a stream containing only first name and surname") {
    // Transforms Stream[IO, Actor] => Stream[IO, String]
    val fromActorToNamePipe: Pipe[IO, Actor, String] = actorStream =>
      actorStream.map(actor => s"${actor.firstName} ${actor.lastName}")

    // Transforms Stream[IO, T] => Stream[IO, Unit]
    def toConsole[T]: Pipe[IO, T, Unit] = tStream => tStream.evalMap(IO.println)

    // The justiceLeagueActors stream represents the source, whereas the fromActorToNamePipe represents a pipe,
    // and the toConsole represents the sink. We can conclude that a pipe is pretty much a map/flatMap
    // type functional operation, but the pipe concept fits nicely into the mental model of a Stream.
    assertIO(justiceLeagueActorsStream.through(fromActorToNamePipe).through(toConsole).compile.drain, ())
  }
}
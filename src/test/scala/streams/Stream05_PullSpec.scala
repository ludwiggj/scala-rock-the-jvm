package streams

import cats.effect.IO
import fs2.{Chunk, Pipe, Pull, Pure, Stream}
import munit.CatsEffectSuite
import streams.Fixture._

class Stream05_PullSpec extends CatsEffectSuite {
  // fs2 defines streams as a pull type, which means that the stream effectively computes the next stream element
  // just in time. The library implements the Stream type functions using the Pull type. This type, also available
  // as a public API, lets us implement streams using the pull model.
  //
  // The Pull[F[_], O, R] type represents a program that can pull output values of type O while computing a result
  // of type R while using an effect of type F. The type introduces the new type variable R that is not available
  // in the Stream type.
  //
  // The result R represents the information available after the emission of the element of type O that should be
  // used to emit the next value of a stream. For this reason, using Pull directly means implementing a recursive
  // program.

  // The Pull type represents a stream as a head and a tail, much like we can describe a list. The element of
  // type O emitted by the Pull represents the head. However, since a stream is a possible infinite data
  // structure, we cannot express it with a finite one. So, we return a type R, which is all the information
  // that we need to compute the tail of the stream.

  // Smart constructor output1 creates a Pull that emits a single value of type O and then completes.
  test("output1 creates stream with single value") {
    val tomHollandActorPull: Pull[Pure, Actor, Unit] = Pull.output1(tomHolland)

    // We can convert a Pull having the R type variable bound to Unit directly to a Stream by using the stream method
    // A Pull that returns Unit is like a List with a head and empty tail.

    // StreamPullOps implicit class (made explicit below) makes the stream method available, but only if R is Unit
    // implicit final class StreamPullOps[F[_], O](private val self: Pull[F, O, Unit]) extends AnyVal
    assertEquals(Pull.StreamPullOps(tomHollandActorPull).stream.toList, List(tomHolland))
  }

  // A Pull forms a monad instance on R. This allows us to concatenate the information that allows us to compute
  // the tail of the stream.
  test("flatmaps pulls together to create stream with multiple values") {
    // So, if we want to create a sequence of Pulls containing all the actors that play
    // Spider-Man, we can do the following:
    val spiderMenActorPull: Pull[Pure, Actor, Unit] =
      Pull.output1(tomHolland) >> Pull.output1(tobeyMaguire) >> Pull.output1(andrewGarfield)

    assertEquals(spiderMenActorPull.stream.toList, spiderMenActors)
  }

  test("flatmaps pulls together to create stream with multiple values - desugared") {
    val spiderMenActorPull: Pull[Pure, Actor, Unit] = for {
      _ <- Pull.output1(tomHolland)
      _ <- Pull.output1(tobeyMaguire)
      _ <- Pull.output1(andrewGarfield)
    } yield ()

    assertEquals(spiderMenActorPull.stream.toList, spiderMenActors)
  }

  // The echo function returns the internal representation of the Stream, called underlying since a stream
  // is represented as a pull internally:
  // final class Stream[+F[_], +O] private[fs2] (private[fs2] val underlying: Pull[F, O, Unit])
  test("Stream.pull.echo returns a pull") {
    // In this example, the first invoked function is pull, which returns a ToPull[F, O] type. This is a
    // wrapper around the Stream type, which groups all functions concerning the conversion into a Pull
    // instance
    // final class ToPull[F[_], O] private[Stream] (private val self: Stream[F, O]) extends AnyVal) {
    //    def echo: Pull[F, O, Unit] = self.underlying
    // }
    val avengersActorsPull: Pull[Pure, Actor, Unit] = avengerActorsStream.pull.echo
    assertEquals(avengersActorsPull.stream.toList, avengerActors)
  }

  // uncons function returns a Pull that pulls a tuple containing the head chunk of the stream and its tail
  test("uncons") {
    // Since the original stream uses the Pure effect i.e. Stream[Pure, Actor], the resulting Pull also uses the
    // same effect. As the Pull deconstructs the original stream, it cannot emit any value, and so the output type
    // is Nothing. The value returned by the Pull is Option[(Chunk[Actor], Stream[Pure, Actor])]]. This represents
    // the deconstruction of the original Stream. The returned value is an Option because the Stream may be empty;
    // if there are no more values in the original Stream then it will be None. Otherwise, we will have the head of
    // the stream as a Chunk and a Stream representing the tail of the original stream.

    val unconsAvengersActors: Pull[Pure, Nothing, Option[(Chunk[Actor], Stream[Pure, Actor])]] =
      avengerActorsStream.pull.uncons

    // Remember: we cannot call stream on unconsAvengersActors as R is not Unit; it is
    // Option[(Chunk[Actor], Stream[Pure, Actor])]

    // i.e. this won't compile:
    //   unconsAvengersActors.stream.compile.toList

    // But we can call void on it to throw the value away, and we can then call stream
    assertEquals(unconsAvengersActors.void.stream.toList, List.empty)
  }

  test("capitalise actor's first name (uncons)") {
    def go(s: Stream[IO, Actor]): Pull[IO, Actor, Unit] =
      s.pull.uncons.flatMap {
        case Some((actorChunk, tailStream)) =>
          Pull.output[IO, Actor](actorChunk.map(actor => actor.copy(firstName = actor.firstName.toUpperCase))) >> go(tailStream)
        case None => Pull.done
      }

    assertIO(go(avengerActorsStream).stream.compile.toList, avengerActors.map(a => a.copy(firstName = a.firstName.toUpperCase)))
  }

  // uncons1 is a variant of the uncons method; it returns the first stream element rather than the first Chunk
  test("capitalise actor's surname (via uncons1)") {
    def go(s: Stream[IO, Actor]): Pull[IO, Actor, Unit] =
      s.pull.uncons1.flatMap {
        case Some((actor, tailStream)) =>
          Pull.output1[IO, Actor](actor.copy(lastName = actor.lastName.toUpperCase)) >> go(tailStream)
        case None => Pull.done
      }

    // But we can call void on it to throw the value away, and we can then call stream
    assertIO(go(avengerActorsStream).stream.compile.toList, avengerActors.map(a => a.copy(lastName = a.lastName.toUpperCase)))
  }

  test("filter actors by first name (via uncons1)") {
    // We can write a pipe filtering a stream of actors on their first name, without using the Stream.filter method
    def takeByName(name: String): Pipe[IO, Actor, Actor] = {
      // We need to accumulate the actors in the stream that fulfill the filtering condition.
      // So, we apply a typical functional programming pattern and define an inner function (go) that we use to recur.
      def go(s: Stream[IO, Actor], name: String): Pull[IO, Actor, Unit] = {
        // We deconstruct the stream using uncons1 to retrieve its first element.
        // Since the Pull type forms a monad on the R type (Option[(Actor, Stream[IO, Actor])]), we can use the flatMap
        // method to recur
        s.pull.uncons1.flatMap {
          case Some((hd, tl)) =>
            // If the actor’s first name representing the head of the stream has the right first name, we output the
            // actor and recur with the tail of the stream.
            if (hd.firstName == name) Pull.output1(hd) >> go(tl, name)
            // Otherwise, we recur with the tail of the stream:
            else go(tl, name)
          case None =>
            // We’re done. Pull.done terminates the recursion, returning a Pull[Pure, INothing, Unit] instance
            Pull.done
        }
      }

      // Finally, we define the whole Pipe calling the go function and use the stream method to convert the
      // Pull instance into a Stream instance
      in => go(in, name).stream
    }

    assertIO(avengerActorsStream.through(takeByName("Chris")).compile.toList, List(chrisEvans, chrisHemsworth))
  }

  test("filter actors on surname starting with K or before (via uncons)") {
    // Similar recursive technique needed to stream when using pull and uncons
    def takeBySurnameStartingWithLetterUpToAndIncluding(letter: Char): Pipe[IO, Actor, Actor] = {
      def go(s: Stream[IO, Actor]): Pull[IO, Actor, Unit] = {
        s.pull.uncons.flatMap {
          case Some((hd, tl)) =>
            val filteredActors = hd.filter(_.lastName.toLowerCase.headOption.exists(_ <= letter.toLower))
            Pull.output(filteredActors) >> go(tl)

          case None =>
            Pull.done
        }
      }

      in => go(in).stream
    }

    assertIO(
      avengerActorsStream.through(takeBySurnameStartingWithLetterUpToAndIncluding(letter = 'K')).compile.toList,
      List(scarlettJohansson, robertDowneyJr, chrisEvans, chrisHemsworth, tomHolland)
    )
  }
}
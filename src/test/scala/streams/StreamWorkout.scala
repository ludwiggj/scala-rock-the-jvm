package streams

import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import cats.effect.{ExitCode, IO, IOApp}
import fs2.{Chunk, Pipe, Pull, Pure, Stream}
import streams.Fixture._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{DurationInt, FiniteDuration}

// This is based on https://blog.rockthejvm.com/fs2/

// TODO: Look at following (from https://github.com/ITV/fast-ingest/commit/bc8673f6b03e529bd628056a8f10540e6f396c23)
// // TODO: Delete as these statements are not being logged
//       def info1(msg: String): Stream[F, Unit] = emit(logger.info(msg))
//
//       // TODO: Delete as these statements are logged, but calls to Amagi in getScheduleForPipe are not made
//       def info2(msg: String): Stream[F, Unit] =
//         emit[F, String](msg)
//           .evalTap(logger.info(_))
//           .flatMap(_ => empty)
//
//       // TODO: Keep this version, as it works
//       def info(msg: String): Stream[F, String] =
//         emit[F, String](msg)
//           .evalTap(logger.info(_))
//
//  Actual solution:
//    def info(msg: String): Stream[F, Unit] = eval(logger.info(msg))

// Running this evaluates the IO[ExitCode]
object StreamWorkout extends IOApp {
  def buildingStreams: (Stream[Pure, Actor], Stream[Pure, Actor], Stream[Pure, Actor]) = {
    (justiceLeagueActorsStream, avengerActorsStream, spiderMenActorsStream)
  }

  // (6) The pull type
  def pullType(avengerActors: Stream[Pure, Actor]): Unit = {
    println("\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
    println("> PULL TYPE                          <")
    println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<")

    // fs2 defines streams as a pull type, which means that the stream effectively computes the next stream element
    // just in time. The library implements the Stream type functions using the Pull type. This type, also available
    // as a public API, lets us implement streams using the pull model.
    //
    // The Pull[F[_], O, R] type represents a program that can pull output values of type O while computing a result
    // of type R while using an effect of type F. The type introduces the new type variable R that is not available
    // in the Stream type.
    //
    // The result R represents the information available after the emission of the element of type O that should be
    // used to emit the next value of a stream. For this reason, using Pull directly means to develop recursive
    // programs.

    // The Pull type represents a stream as a head and a tail, much like we can describe a list. The element of
    // type O emitted by the Pull represents the head. However, since a stream is a possible infinite data
    // structure, we cannot express it with a finite one. So, we return a type R, which is all the information
    // that we need to compute the tail of the stream.
    //
    // Smart constructor output1 creates a Pull that emits a single value of type O and then completes.

    println("\nPull.output1 creates stream with single value")
    println("=============================================\n")

    val tomHollandActorPull: Pull[Pure, Actor, Unit] = Pull.output1(tomHolland)

    // We can convert a Pull having the R type variable bound to Unit directly to a Stream by using the stream method
    // A Pull that returns Unit is like a List with a head and empty tail.

    // StreamPullOps implicit class (made explicit below) shows how stream is only available if R is Unit
    // implicit final class StreamPullOps[F[_], O](private val self: Pull[F, O, Unit]) extends AnyVal

    println(Pull.StreamPullOps(tomHollandActorPull).stream.compile.toList)

    println("\nPull.>> flatmaps pulls together to create stream with multiple values")
    println("=====================================================================\n")

    // A Pull forms a monad instance on R. This allows us to concatenate the information that allows us to compute
    // the tail of the stream. So, if we want to create a sequence of Pulls containing all the actors that play
    // Spider-Man, we can do the following:

    val spiderMenActorPull: Pull[Pure, Actor, Unit] =
      tomHollandActorPull >> Pull.output1(tobeyMaguire) >> Pull.output1(andrewGarfield)
    println(spiderMenActorPull.stream.compile.toList)

    println(tomHollandActorPull.flatMap(Unit => Pull.output1(tobeyMaguire)).stream.compile.toList)

    println("\nStream.echo returns a pull")
    println("==========================\n")
    val avengersActorsPull: Pull[Pure, Actor, Unit] = avengerActors.pull.echo
    println(avengersActorsPull.stream.compile.toList)

    // In the above example, the first invoked function is pull, which returns a ToPull[F, O] type. This is a
    // wrapper around the Stream type, which groups all functions concerning the conversion into a Pull
    // instance

    // final class ToPull[F[_], O] private[Stream] (private val self: Stream[F, O]) extends AnyVal) {
    //    def echo: Pull[F, O, Unit] = self.underlying
    // }

    // The echo function returns the internal representation of the Stream, called underlying since a stream
    // is represented as a pull internally:

    // final class Stream[+F[_], +O] private[fs2] (private[fs2] val underlying: Pull[F, O, Unit])

    // Stream.uncons and uncons1 return a pull

    // uncons function returns a Pull that pulls a tuple containing the head chunk of the stream and its tail

    // Since the original stream uses the Pure effect, the resulting Pull also uses the same effect. As the Pull
    // deconstructs the original stream, it cannot emit any value, and so the output type is INothing (type
    // alias for scala Nothing). The value returned by the Pull represents the deconstruction of the original
    // Stream. The returned value is an Option because the Stream may be empty: If there are no more values in
    // the original Stream then we will have a None. Otherwise, we will have the head of the stream as a Chunk
    // and a Stream representing the tail of the original stream.

    val unconsAvengersActors: Pull[Pure, Nothing, Option[(Chunk[Actor], Stream[Pure, Actor])]] =
      avengerActors.pull.uncons

    // Remember: we cannot call stream on unconsAvengersActors as R is not Unit; it is
    // Option[(Chunk[Actor], Stream[Pure, Actor])]

    // println(unconsAvengersActors.stream.compile.toList)

    // uncons1 is a variant of the uncons method; it returns the first stream element rather than the first Chunk
    val uncons1AvengersActors: Pull[Pure, Nothing, Option[(Actor, Stream[Pure, Actor])]] =
      avengerActors.pull.uncons1

    // Let's define some functions that use the Pull type. Due to the structure of the type, the functions
    // implemented using the Pull type are often recursive.

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

    println("\nFilter Avengers actors on first name (Chris), using pull type (uncons1)")
    println("=======================================================================\n")

    def toConsole[T]: Pipe[IO, T, Unit] = in =>
      in.evalMap(IO.println)

    avengerActors.through(takeByName("Chris")).through(toConsole).compile.drain.unsafeRunSync

    // Similar recursive technique needed to stream when using pull and uncons
    def takeBySurnameBefore(letter: Char): Pipe[IO, Actor, Actor] = {
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

    println("\nFilter Avengers actors on surname starting with K or before, using pull type (uncons) x 3")
    println("=========================================================================================\n")

    val wakingStreamDurations: Stream[IO, FiniteDuration] =
      Stream.awakeEvery[IO](FiniteDuration(1, TimeUnit.SECONDS)).take(3)

    val wakingStreamActors: Stream[IO, Actor] = wakingStreamDurations.flatMap(_ => avengerActors)

    wakingStreamActors.through(takeBySurnameBefore('K')).through(toConsole).compile.drain.unsafeRunSync
  }

  // (7) Concurrent streams
  def concurrentStreams(avengerActors: Stream[Pure, Actor],
                        justiceLeagueActors: Stream[Pure, Actor],
                        spiderActors: Stream[Pure, Actor]): Unit = {
    println("\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
    println("> CONCURRENT STREAMS                          <")
    println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<")

    def toConsole[T]: Pipe[IO, T, Unit] = in =>
      in.evalMap(IO.println)

    val concurrentAvengerActors: Stream[IO, Actor] = avengerActors.evalMap(actor => IO {
      Thread.sleep(200)
      actor
    })

    val concurrentJusticeLeagueActors: Stream[IO, Actor] = justiceLeagueActors.evalMap(actor => IO {
      Thread.sleep(400)
      actor
    })

    println("\nMerged Avengers/Justice Leaguers actors, 2:1 ratio (merge)")
    println("==========================================================\n")

    // merge, which lets us run two streams concurrently, collecting the results in a single stream as
    // they are produced. The execution halts when both streams have halted

    // the stream contains a Justice League actor more or less every two Avenger actors.
    // Once the Avengers actors are finished, the JLA actors fulfill the rest of the stream.
    val mergedActors: Stream[IO, Unit] = concurrentJusticeLeagueActors
      .merge(concurrentAvengerActors)
      .through(toConsole)
    mergedActors.compile.drain.unsafeRunSync

    // concurrently runs the second stream in the background and terminates the second stream
    // once the first stream terminates. Typically used when don't care about results of
    // second stream

    // In this case, we are explicitly printing results of second stream to demonstrate what's happening
    println("\nConcurrent Justice Leaguers/Avengers actors, 1:2 ratio")
    println("======================================================\n")

    val concurrentJLAAvengers: Stream[IO, Unit] = concurrentJusticeLeagueActors
      .concurrently(concurrentAvengerActors.through(toConsole))
      .through(toConsole)
    concurrentJLAAvengers.compile.drain.unsafeRunSync

    println("\nConcurrent Avengers/Justice Leaguers actors, 2:1 ratio")
    println("======================================================\n")

    val concurrentAvengersJLA: Stream[IO, Unit] = concurrentAvengerActors
      .concurrently(concurrentJusticeLeagueActors.through(toConsole))
      .through(toConsole)
    concurrentAvengersJLA.compile.drain.unsafeRunSync


    // An everyday use case for this method is implementing a producer-consumer pattern.
    // Following defines a producer that uses a cats.effect.std.Queue to share JLA actors
    // with a consumer, which prints them to the console

    println("\nProducer/Consumer Stream Pattern")
    println("================================\n")
    val queue: IO[Queue[IO, Actor]] = Queue.bounded[IO, Actor](10)

    val queueStream: Stream[IO, Queue[IO, Actor]] = Stream.eval(queue)

    val concurrentlyStreams: Stream[IO, Unit] = queueStream.flatMap { q => // q: Queue[IO, Actor]

      val producer: Stream[IO, Unit] =
        avengerActors
          .evalTap(actor => IO.println(s"[${Thread.currentThread().getName}] produced $actor"))
          .evalMap(q.offer)
          .metered(1.second) // time between consecutive pulls

      val consumer: Stream[IO, Unit] =
        Stream.fromQueueUnterminated(q)
          .evalMap(actor => IO.println(s"[${Thread.currentThread().getName}] consumed $actor"))

      // A critical feature of running two streams through the concurrently method is that the
      // second stream halts when the first stream is finished. In this case, there's no point
      // trying to consumer more when the producer has finished
      producer.concurrently(consumer)
    }

    concurrentlyStreams.compile.drain.unsafeRunSync()

    println("\nparJoin")
    println("=======\n")
    // We can run a set of streams concurrently, deciding the degree of concurrency a priori using streams.
    // The method parJoin does precisely this. Contrary to the concurrently method, the results of the
    // streams are merged in a single stream, and no assumption is made on streams’ termination.

    // We can try to print the actors playing superheroes of the JLA and the Avengers concurrently.

    // First, we define a Pipe printing the thread name and the actor being processed:
    val toConsoleWithThread: Pipe[IO, Actor, Unit] = in =>
      in.evalMap(actor => IO.println(s"[${Thread.currentThread().getName}] consumed $actor"))

    // Now define the stream using parJoin
    // parJoin non-deterministically merges a stream of streams (outer) in to a single stream, opening at
    // most maxOpen streams at any point in time.

    // The method is available only on streams of type Stream[F, Stream[F, O]]. It’s not part directly of
    // the Stream API, but an implicit conversion adds it as an extension method:

    // fs2 library code
    //  implicit final class NestedStreamOps[F[_], O](private val outer: Stream[F, Stream[F, O]]) extends AnyVal {
    //  def parJoin(maxOpen: Int)(implicit F: Concurrent[F]): Stream[F, O] = ???
    // }

    // Each stream is traversed concurrently, and the maximum degree of concurrency is given in input as the
    // parameter maxOpen. As we can see, the effect used to handle concurrency must satisfy the Concurrent bound,
    // which is required anywhere concurrency is used in the library.

    val actorStream: Stream[Pure, Stream[IO, Unit]] = Stream(
      justiceLeagueActors.through(toConsoleWithThread),
      avengerActors.through(toConsoleWithThread),
      spiderActors.through(toConsoleWithThread),
    )

    println("\nparJoin (all sequential)")
    println("========================\n")

    val parJoinedActors: Stream[IO, Unit] =
      actorStream.parJoin(maxOpen = 1)

    parJoinedActors.compile.drain.unsafeRunSync()

    println("\nparJoin (some mixed)")
    println("====================\n")

    actorStream.parJoin(maxOpen = 2).compile.drain.unsafeRunSync()

    println("\nparJoin (all mixed)")
    println("===================\n")

    actorStream.parJoin(maxOpen = 3).compile.drain.unsafeRunSync()

    // Many other concurrency methods are available e.g. either and mergeHaltBoth
  }

  override def run(args: List[String]): IO[ExitCode] = {
    val (justiceLeagueActors, avengerActors, spiderActors) = buildingStreams

    pullType(avengerActors)

    concurrentStreams(avengerActors, justiceLeagueActors, spiderActors)

    println("\nRunning the IOApp (the preferred way)")
    println("=====================================\n")

    // We can run the application as an IOApp (the preferred way)
    justiceLeagueActors.covary[IO].compile.drain.as(ExitCode.Success)
  }
}

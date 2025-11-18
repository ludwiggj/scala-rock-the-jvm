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
    def toConsole[T]: Pipe[IO, T, Unit] = in =>
      in.evalMap(IO.println)

    val wakingStreamDurations: Stream[IO, FiniteDuration] =
      Stream.awakeEvery[IO](FiniteDuration(1, TimeUnit.SECONDS)).take(3)

    val wakingStreamActors: Stream[IO, Actor] = wakingStreamDurations.flatMap(_ => avengerActors)
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

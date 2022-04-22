package streams

import cats.MonadThrow
import cats.effect.{ExitCode, IO, IOApp}
import cats.effect.unsafe.implicits.global
import streams.Data._
import streams.Model.Actor
import fs2.{Chunk, Pure, Stream}

// Running this evaluates the IO[ExitCode]
object StreamWorkout extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    // Pure streams evaluate no effects, they cannot fail
    val jlActors: Stream[Pure, Actor] = Stream(
      henryCavil,
      galGodot,
      ezraMiller,
      benFisher,
      rayHardy,
      jasonMomoa
    )

    val tomHollandStream: Stream[Pure, Actor] = Stream.emit(tomHolland)

    val spiderMen: Stream[Pure, Actor] = Stream.emits(List(
      tomHolland,
      tobeyMaguire,
      andrewGarfield
    ))

    // Convert streams to other structures
    val jlActorList: List[Actor] = jlActors.toList
    val jlActorVector: Vector[Actor] = jlActors.toVector

    // Infinite streams
    val infiniteJLActors: Stream[Pure, Actor] = jlActors.repeat

    println("Actors twice")
    infiniteJLActors.take(12).toList.foreach(println)

    // Lift stream into an IO
    val liftedJLActors: Stream[IO, Actor] = jlActors.covary[IO]
    val liftedJLActors2: Stream[IO, Actor] = jlActors

    // lifting into a different effect type
    def jlActorStream[F[_]: MonadThrow]: Stream[F, Actor] = jlActors.covary[F]

    // eval creates a single element stream that gets its value by evaluating
    // the supplied effect. If the effect fails, the returned stream fails.
    val savingTomHolland: Stream[IO, Unit] = Stream.eval {
      IO {
        println(s"Saving actor $tomHolland")
        Thread.sleep(1000)
        println("Finished")
      }
    }

    // drain method, which discards any effect output
    val compiledStream: IO[Unit] = savingTomHolland.compile.drain
    compiledStream.unsafeRunSync()

    println("IO Actors")
    val actors = liftedJLActors.compile.toList.unsafeRunSync()
    actors.foreach(println)

    // drain to discard the effect
    val drainedActors: Unit = liftedJLActors.compile.drain.unsafeRunSync()

    // Chunks
    // Chunk[O] is a finite sequence of stream elements of type O stored inside a structure
    // optimized for indexed based lookup of elements.
    val chunkedAvengers: Chunk[Actor] = Chunk.array(Array(
      scarlettJohansson,
      robertDowneyJr,
      chrisEvans,
      markRuffalo,
      chrisHemsworth,
      jeremyRenner
    ))

    val streamedAvengers: Stream[Pure, Actor] = Stream.chunk(chunkedAvengers)



    savingTomHolland.compile.drain.as(ExitCode.Success)
  }
}

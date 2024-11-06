import cats.MonadThrow
import cats.effect.IO
import cats.implicits.catsSyntaxOptionId
import fs2.{Chunk, Pure, Stream}
import munit.CatsEffectSuite
import streams.Data._
import streams.Model.Actor

class StreamBuildingTest extends CatsEffectSuite {
  val justiceLeagueActors: List[Actor] = List(
    henryCavil,
    galGodot,
    ezraMiller,
    benFisher,
    rayHardy,
    jasonMomoa
  )

  val spiderMenActors: List[Actor] = List(
    tomHolland,
    tobeyMaguire,
    andrewGarfield
  )

  val avengerActors: List[Actor] = List(
    scarlettJohansson,
    robertDowneyJr,
    chrisEvans,
    markRuffalo,
    chrisHemsworth,
    jeremyRenner,
    tomHolland
  )

  // Pure streams evaluate no effects, they cannot fail
  test("convert pure stream into a list") {
    val justiceLeagueActorStream = Stream(justiceLeagueActors: _*)
    assertEquals(
      justiceLeagueActorStream.toList,
      justiceLeagueActors
    )
  }

  // Pure streams evaluate no effects, they cannot fail
  test("convert pure stream into a vector") {
    val justiceLeagueActorStream = Stream(justiceLeagueActors: _*)
    val stream = justiceLeagueActorStream
    assertEquals(
      stream.toVector,
      justiceLeagueActors.toVector
    )
  }

  test("emit creates a pure stream") {
    val tomHollandStream: Stream[Pure, Actor] = Stream.emit(tomHolland)
    assertEquals(
      tomHollandStream.compile.toList,
      List(tomHolland)
    )
  }

  test("emits creates a pure stream") {
    val spiderMenActorStream: Stream[Pure, Actor] = Stream.emits(spiderMenActors)
    assertEquals(
      spiderMenActorStream.toList,
      spiderMenActors
    )
  }

  // Infinite stream
  test("taking from an infinite pure stream") {
    val infiniteJusticeLeagueActors: Stream[Pure, Actor] = Stream.emits(justiceLeagueActors).repeat
    assertEquals(
      infiniteJusticeLeagueActors.take(12).toList,
      justiceLeagueActors ++ justiceLeagueActors
    )
  }

  // Lift stream into an IO
  // The Pure effect is not sufficient to pull new elements from a stream most of the time.
  // Typically the stream must interact with some external resource or with some code performing side effects.
  // This means the operation can fail. In this case, we need to use some effect library, such as Cats-effect,
  // and its effect type, called IO[A]

  // IO represents the “functional” and “effectful” parts. All the streams’ definitions are referentially
  // transparent and remain pure since no side effects are performed.

  // Starting from the stream we already defined, we can create a new effectful stream mapping the Pure effect
  // in an IO effect using the covary[F] method
  test("lifting a pure stream into an effect type") {
    val liftedJusticeLeagueActors: Stream[IO, Actor] = Stream.emits(justiceLeagueActors).covary[IO]
    assertIO(
      // Once an effectful stream is compiled we have options e.g. convert to a list
      liftedJusticeLeagueActors.compile.toList,
      justiceLeagueActors
    )
  }

  // Pure can be lifted to IO automatically if target type is specified
  test("lifting a pure stream into an effect type without specifying the target type") {
    val liftedJusticeLeagueActors: Stream[IO, Actor] = Stream.emits(justiceLeagueActors)
    assertIO(
      liftedJusticeLeagueActors.compile.toList,
      justiceLeagueActors
    )
  }

  // The method is called covary because of the covariance of the Stream type in the F type parameter:
  //
  // fs2 library code
  // final class Stream[+F[_], +O]
  //
  // Since the Pure effect is defined as an alias of the Scala bottom type Nothing, the covary method takes
  // advantage of this fact and changes the effect type to IO:
  //
  // Covariance in F means that
  // Pure <: IO => Stream[Pure, O] <: Stream[IO, O]

  // It’s not mandatory to use the IO effect. We can use any other effect library that supports type classes
  // defined in the cats-effect library. As an example, let’s use the covary method using a generic effect type.
  // In this case, we chose to use the MonadThrow type class from the cats-effect library, which allows us to
  // concatenate effects using the flatMap method and handle exceptions natively.
  test("lifting a pure stream into IO via MonadThrow") {
    def justiceLeagueActorStream[F[_] : MonadThrow]: Stream[F, Actor] = Stream.emits(justiceLeagueActors).covary[F]

    assertIO(
      justiceLeagueActorStream[IO].compile.toList,
      justiceLeagueActors
    )
  }

  // In most cases, we want to create a stream directly evaluating some statements that may produce side effects.
  // So, for example, we can simulate persisting an actor through a stream
  test("drain an effectful stream containing a single element") {
    var effectMsg = Option.empty[String]
    val persistedMsg = s"Saved actor $tomHolland"

    // eval creates a single element effectful stream
    val savingTomHolland: Stream[IO, Unit] = Stream.eval {
      IO {
        println(s"Saving actor $tomHolland")
        Thread.sleep(1000)
        println(persistedMsg)
        effectMsg = persistedMsg.some
      }
    }

    // The stream will evaluate the IO effect when pulled.
    // To pull a value we need to compile the stream into a single instance of the effect (as above)
    val compiledStream: Stream.CompileOps[IO, IO, Unit] = savingTomHolland.compile

    // Call the drain method, which evaluates the effect and then discards the output
    for {
      _ <- assertIO(compiledStream.drain, ())
      _ <- assertIO(IO(effectMsg), persistedMsg.some)
    } yield ()
  }

  // Chunks - Every stream is made of chunks

  // Chunk[O] is a finite sequence of stream elements of type O stored inside a structure
  // optimized for indexed based lookup of elements.

  // We can create a stream directly through the Stream.chunk method, which accepts a sequence of Chunk
  test("create pure stream from chunks") {
    val chunkedAvengers: Chunk[Actor] = Chunk.from(avengerActors)
    assertEquals(Stream.chunk(chunkedAvengers).toList, avengerActors)
  }

  // The fs2 library defines a lot of smart-constructors for the Chunk type, letting us create a
  // Chunk from an Option, a Seq, a Queue, and so on.
  //
  // Most of the functions defined on streams are Chunk- aware, so we don’t have to worry about
  // chunks while working with them.
}
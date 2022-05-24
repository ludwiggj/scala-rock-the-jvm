package streams

import cats.MonadThrow
import cats.effect.{ExitCode, IO, IOApp}
import cats.effect.unsafe.implicits.global
import streams.Data._
import streams.Model.Actor
import fs2.{Chunk, INothing, Pipe, Pull, Pure, Stream}

import scala.util.{Failure, Random, Try}

// Running this evaluates the IO[ExitCode]
object StreamWorkout extends IOApp {
  object db {
    case class DatabaseConnection(connection: String) extends AnyVal
  }

  override def run(args: List[String]): IO[ExitCode] = {
    // (2) Building a stream

    // Pure streams evaluate no effects, they cannot fail
    val jlActors: Stream[Pure, Actor] = Stream(
      henryCavil,
      galGodot,
      ezraMiller,
      benFisher,
      rayHardy,
      jasonMomoa
    )

    // Can use emit and emits to create pure streams
    val tomHollandStream: Stream[Pure, Actor] = Stream.emit(tomHolland)

    val spiderMen: Stream[Pure, Actor] = Stream.emits(List(
      tomHolland,
      tobeyMaguire,
      andrewGarfield
    ))

    // Convert pure streams to other structures
    val jlActorList: List[Actor] = jlActors.toList
    val jlActorVector: Vector[Actor] = jlActors.toVector

    // Infinite streams
    val infiniteJLActors: Stream[Pure, Actor] = jlActors.repeat

    println("Actors twice")
    infiniteJLActors.take(12).toList.foreach(println)

    // Lift stream into an IO
    // The Pure effect is not sufficient to pull new elements from a stream most of the time.
    // Typically the stream must interact with some external resource or with some code performing side effects.
    // This means the operation can fail. In this case, we need to use some effect library, such as Cats-effect,
    // and its effect type, called IO[A]

    // IO represents the “functional” and “effectful” parts. All the streams’ definitions are referentially
    // transparent and remain pure since no side effects are performed.

    // Starting from the stream we already defined, we can create a new effectful stream mapping the Pure effect
    // in an IO effect using the covary[F] method:
    val liftedJLActors: Stream[IO, Actor] = jlActors.covary[IO]
    val liftedJLActors2: Stream[IO, Actor] = jlActors

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

    // lifting into a different effect type

    // It’s not mandatory to use the IO effect. We can use any other effect library that supports type classes
    // defined in the cats-effect library. As an example, let’s use the covary method using a generic effect type.

    // In this case, we chose to use the MonadThrow type class from the cats-effect library, which allows us to
    // concatenate effects using the flatMap method and handle exceptions natively.
    def jlActorStream[F[_]: MonadThrow]: Stream[F, Actor] = jlActors.covary[F]

    // In most cases, we want to create a stream directly evaluating some statements that may produce side effects.
    // So, for example, let’s try to persist an actor through a stream:

    // eval creates a single element stream that gets its value by evaluating
    // the supplied effect. If the effect fails, the returned stream fails.

    // The stream will evaluate the IO effect when pulled.
    val savingTomHolland: Stream[IO, Unit] = Stream.eval {
      IO {
        println(s"Saving actor $tomHolland")
        Thread.sleep(1000)
        println("Finished")
      }
    }

    // To pull a value we need to compile the stream into a single instance of the effect
    // Also applied the drain method, which discards any effect output
    val compiledStream: IO[Unit] = savingTomHolland.compile.drain
    compiledStream.unsafeRunSync()

    // Once stream is compiled we have options e.g. convert to a list
    println("IO Actors")
    val actors: IO[List[Actor]] = liftedJLActors.compile.toList
    actors.unsafeRunSync().foreach(println)

    // drain to discard the effect
    val drainedActors: Unit = liftedJLActors.compile.drain.unsafeRunSync()

    // (2.1) Chunks

    // Every stream is made of chunks

    // Chunk[O] is a finite sequence of stream elements of type O stored inside a structure
    // optimized for indexed based lookup of elements.

    // We can create a stream directly through the Stream.chunk method, which accepts a sequence of Chunk
    val chunkedAvengers: Chunk[Actor] = Chunk.array(Array(
      scarlettJohansson,
      robertDowneyJr,
      chrisEvans,
      markRuffalo,
      chrisHemsworth,
      jeremyRenner
    ))

    val streamedAvengers: Stream[Pure, Actor] = Stream.chunk(chunkedAvengers)

    // The fs2 library defines a lot of smart-constructors for the Chunk type, letting us create a
    // Chunk from an Option, a Seq, a Queue, and so on.
    //
    // Most of the functions defined on streams are Chunk- aware, so we don’t have to worry about
    // chunks while working with them.

    // (3) Transforming a stream

    // concatenate two streams
    val dcAndMarvelSuperheroes: Stream[Pure, Actor] = jlActors ++ streamedAvengers

    // The Stream type forms a monad on the O type parameter, which means that a flatMap method
    // is available on streams, and we can use it to concatenate operations concerning the
    // output values of the stream.

    // For example, printing to the console the elements of a stream uses the flatMap method:

    val printedJlActors: Stream[IO, Unit] = jlActors.flatMap { actor =>
      Stream.eval(IO.println(Actor))
    }

    // The pattern of calling the function Stream.eval inside a flatMap is so common that fs2
    // provides a shortcut for it through the evalMap method:
    val evalMappedJlActors: Stream[IO, Unit] = jlActors.evalMap(IO.println)

    // If we need to perform some effects on the stream, but we don’t want to change the type
    // of the stream, we can use the evalTap method:
    val evalTappedJlActors: Stream[IO, Actor] = jlActors.evalTap(IO.println)

    // An essential feature of fs2 streams is that their functions take constant time, regardless
    // of the structure of the stream itself. So, concatenating two streams is a constant time
    // operation, whether the streams contain many elements or are infinite. As we will see in
    // the rest of the article, this feature concerns the internal representation, which is
    // implemented as a pull-based structure.

    // group Avengers by actor's name
    // fold works as per usual
    val avengersActorsByFirstName: Stream[Pure, Map[String, List[Actor]]] = streamedAvengers.fold(Map.empty[String, List[Actor]]) { case (map, actor) =>
      map + (actor.firstName -> (actor :: map.getOrElse(actor.firstName, Nil)))
    }

    val groupedAvengers: cats.Id[List[Map[String, List[Actor]]]] = avengersActorsByFirstName.compile.toList
    println(groupedAvengers)

    // Many other streaming libraries define streams and transformation in terms of sources, pipes, and sinks.
    // A source generates the elements of a stream, then transformed through a sequence of stages or pipes,
    // and finally consumed by a sink. For example, the Akka Stream library has specific types modeling these
    // concepts.

    // In fs2, the only available type modeling the above streaming concepts is Pipe[F[_], -I, +O]. Pipe is a
    // type alias for the function Stream[F, I] => Stream[F, O]. So, a Pipe[F[_], I, O] represents nothing more
    // than a function between two streams, the first emitting elements of type I, and the second emitting
    // elements of type O.

    // Using pipes, we can look at the definition of fs2 streams, like the definitions of streams in other
    // libraries, representing transformations on streams as a sequence of stages. The through method
    // applies a pipe to a stream. Its internal implementation is straightforward since it applies the
    // function defined by the pipe to the stream:

    // fs2 library code
    // def through[F2[x] >: F[x], O2](f: Stream[F, O] => Stream[F2, O2]): Stream[F2, O2] = f(this)

    // transform jlActors into stream containing only first name and surname
    val fromActorToStringPipe: Pipe[IO, Actor, String] = in =>
      in.map(actor => s"${actor.firstName} ${actor.lastName}")

    def toConsole[T]: Pipe[IO, T, Unit] = in =>
      in.evalMap(IO.println)

    // The jlActors stream represents the source, whereas the fromActorToStringPipe represents a pipe,
    // and the toConsole represents the sink. We can conclude that a pipe is pretty much a map/flatMap
    // type functional operation, but the pipe concept fits nicely into the mental model of a Stream.
    val stringNamesOfActors: Stream[IO, Unit] = jlActors.through(fromActorToStringPipe).through(toConsole)

    stringNamesOfActors.compile.drain.unsafeRunSync

    // (4) Error Handling

    // What if pulling a value from a stream fails with an exception?

    // Let’s introduce a repository persisting an Actor
    object ActorRepository {
      def save(actor: Actor): IO[Int] = IO {
        println(s"Saving actor: $actor")
        if (Random.nextInt() % 2 == 0) {
          throw new RuntimeException("Something went wrong during the communication with the persistence layer")
        }
        println("Saved")
        actor.id
      }
    }

    val savedJlActors: Stream[IO, Int] = jlActors.evalMap(ActorRepository.save)

    // The stream is interrupted by the exception. Every time an exception occurs during pulling
    // elements from a stream, the stream execution ends.
    Try {
      savedJlActors.compile.drain.unsafeRunSync
    } match {
      case Failure(exception) => println(s"Exception: $exception")
      case _ => ()
    }

    // Handle error by returning a new stream using the handleErrorWith method

    // fs2 library code

    // def handleErrorWith[F2[x] >: F[x], O2 >: O](h: Throwable => Stream[F2, O2]): Stream[F2, O2] = ???

    // The elements contained in the stream are AnyVal and not Unit because of the definition of handleErrorWith

    // The O2 type must be a supertype of O’s original type (Int). Since both Int and Unit are subtypes of AnyVal,
    // we can use the AnyVal type (the least common supertype) to represent the resulting stream.
    val errorHandledSavedJlActors: Stream[IO, AnyVal] = savedJlActors.handleErrorWith( // Stream[IO, Int]
      error => Stream.eval(IO.println(s"handleErrorWith Error: $error")) // Stream[IO, Unit]
    )

    errorHandledSavedJlActors.compile.drain.unsafeRunSync

    // The attempt method works using the scala.Either type, which returns a stream of Either elements. The
    // resulting stream pulls elements wrapped in a Right instance until the first error occurs, which is
    // represented as an instance of a Throwable wrapped in a Left
    val attemptedSavedActors: Stream[IO, Either[Throwable, Int]] = savedJlActors.attempt

    attemptedSavedActors.evalMap {
      case Left(error) => IO.println(s"attempt Error: $error")
      case Right(id) => IO.println(s"Saved actor with id: $id")
    }.compile.drain.unsafeRunSync

    // attempt method is implemented internally using the handleErrorWith

    // fs2 library code
    // def attempt: Stream[F, Either[Throwable, O]] =
    //   map(Right(_): Either[Throwable, O])                // Stream[F, Either[Throwable, O]]
    //     .handleErrorWith(e => Stream.emit(Left(e)))

    // (5) Resource Management

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

    // First, we define a value class representing a connection to a database:

    // We use the DatabaseConnection as the resource we want to acquire and release through
    // the bracket pattern. Then, the acquiring and releasing function:
    import db.DatabaseConnection

    val acquire = IO {
      val conn = DatabaseConnection("jlaConnection")
      println(s"Acquiring connection to the database: $conn")
      conn
    }

    val release = (conn: DatabaseConnection) =>
      IO.println(s"Releasing connection to the database: $conn")

    // Finally, we use them to call the bracket method and then save the actors in the stream
    val managedJlActors: Stream[IO, Int] = Stream.bracket(acquire)(release).flatMap(conn => savedJlActors) // conn would be used in real-life code

    // Since savedJlActors throws an exception when the stream is evaluated, the managedJlActors stream will
    // terminate with an error. The release function is called, and the conn value is released
    managedJlActors.compile.drain.unsafeRunSync

    // (6) The pull type
    val tomHollandActorPull: Pull[Pure, Actor, Unit] = Pull.output1(tomHolland)
    tomHollandActorPull.stream

    val spiderMenActorPull: Pull[Pure, Actor, Unit] =
      tomHollandActorPull >> Pull.output1(tobeyMaguire) >> Pull.output1(andrewGarfield)

    tomHollandActorPull.flatMap(Unit => Pull.output1(tobeyMaguire))

    val avengersActorsPull: Pull[Pure, Actor, Unit] = streamedAvengers.pull.echo

    val unconsAvengersActors: Pull[Pure, INothing, Option[(Chunk[Actor], Stream[Pure, Actor])]] = streamedAvengers.pull.uncons

    val uncons1AvengersActors: Pull[Pure, INothing, Option[(Actor, Stream[Pure, Actor])]] =
      streamedAvengers.pull.uncons1

    def takeByName(name: String): Pipe[IO, Actor, Actor] = {
      def go(s: Stream[IO, Actor], name: String): Pull[IO, Actor, Unit] =
        s.pull.uncons1.flatMap {
          case Some((hd, tl)) =>
            if (hd.firstName == name) Pull.output1(hd) >> go(tl, name)
            else go(tl, name)
          case None => Pull.done
        }

      in => go(in, name).stream
    }

    // (2) We can run the application as an IOApp (the preferred way)
    savingTomHolland.compile.drain.as(ExitCode.Success)
  }
}

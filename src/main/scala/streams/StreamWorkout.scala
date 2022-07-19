package streams

import cats.MonadThrow
import cats.effect.std.Queue
import cats.effect.{ExitCode, IO, IOApp}
import cats.effect.unsafe.implicits.global
import streams.Data._
import streams.Model.Actor
import fs2.{Chunk, INothing, Pipe, Pull, Pure, Stream}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{DurationInt, FiniteDuration}
import scala.util.{Failure, Random, Try}

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
  object db {
    case class DatabaseConnection(connection: String) extends AnyVal
  }

  // (2) Building a stream
  def buildingStreams: (Stream[Pure, Actor], Stream[Pure, Actor], Stream[Pure, Actor]) = {
    println(">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
    println("> BUILDING STREAMS                   <")
    println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<")

    // Pure streams evaluate no effects, they cannot fail
    val justiceLeagueActors: Stream[Pure, Actor] = Stream(
      henryCavil,
      galGodot,
      ezraMiller,
      benFisher,
      rayHardy,
      jasonMomoa
    )

    // Can use emit and emits to create pure streams
    println("\nUsing Stream.emit and Stream.emits to create pure streams")
    println("=========================================================\n")

    val tomHollandStream: Stream[Pure, Actor] = Stream.emit(tomHolland)
    println(s"Tom!\n\n${tomHollandStream.compile.toList.head}\n")

    val spiderMenActors: Stream[Pure, Actor] = Stream.emits(List(
      tomHolland,
      tobeyMaguire,
      andrewGarfield
    ))

    println(s"Spidymen!\n\n${spiderMenActors.compile.toList.mkString("\n")}")

    // Convert pure streams to other structures
    println("\nPure streams as finite structures")
    println("=================================\n")

    val justiceLeagueActorList: List[Actor] = justiceLeagueActors.toList
    val justiceLeagueActorVector: Vector[Actor] = justiceLeagueActors.toVector

    println(s"A list:\n$justiceLeagueActorList")
    println(s"A vector:\n$justiceLeagueActorVector")

    // Infinite streams
    val infiniteJusticeLeagueActors: Stream[Pure, Actor] = justiceLeagueActors.repeat

    println("\nTaking items from a repeating (infinite) stream")
    println("===============================================\n")
    println(s"Actors twice:\n\n${infiniteJusticeLeagueActors.take(12).toList.mkString("\n")}")

    // Lift stream into an IO
    // The Pure effect is not sufficient to pull new elements from a stream most of the time.
    // Typically the stream must interact with some external resource or with some code performing side effects.
    // This means the operation can fail. In this case, we need to use some effect library, such as Cats-effect,
    // and its effect type, called IO[A]

    // IO represents the “functional” and “effectful” parts. All the streams’ definitions are referentially
    // transparent and remain pure since no side effects are performed.

    println("\nLifting streams into an effect type")
    println("===================================\n")

    // Starting from the stream we already defined, we can create a new effectful stream mapping the Pure effect
    // in an IO effect using the covary[F] method:
    val liftedJusticeLeagueActors: Stream[IO, Actor] = justiceLeagueActors.covary[IO]

    println(s"Justice League actors (lifted into IO)               : ${liftedJusticeLeagueActors.compile.toList.unsafeRunSync}")

    // Pure can be lifted to IO automatically if target type is specified
    val liftedJusticeLeagueActors2: Stream[IO, Actor] = justiceLeagueActors

    println(s"Justice League actors2 (lifted into IO)              : ${liftedJusticeLeagueActors2.compile.toList.unsafeRunSync}")

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
    def justiceLeagueActorStream[F[_] : MonadThrow]: Stream[F, Actor] = justiceLeagueActors.covary[F]

    println(s"Justice League actors (lifted into IO via MonadThrow): ${justiceLeagueActorStream[IO].compile.toList.unsafeRunSync}")

    // In most cases, we want to create a stream directly evaluating some statements that may produce side effects.
    // So, for example, let’s try to persist an actor through a stream:

    // eval creates a single element stream that gets its value by evaluating
    // the supplied effect. If the effect fails, the returned stream fails.

    println("\nStream.eval creates single element stream in an effect type")
    println("===========================================================\n")

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
    println("Using drain to run the side-effect, discarding any result:\n")

    val compiledStream: IO[Unit] = savingTomHolland.compile.drain
    compiledStream.unsafeRunSync()

    // Once stream is compiled we have options e.g. convert to a list
    println("\nConverting the stream to a list:\n")
    val actors: IO[List[Actor]] = liftedJusticeLeagueActors.compile.toList
    actors.unsafeRunSync().foreach(println)

    // drain to discard the effect
    println("\nDraining a stream without an observable side-effect:")
    val drainedActors: Unit = liftedJusticeLeagueActors.compile.drain.unsafeRunSync()

    // (2.1) Chunks

    // Every stream is made of chunks

    // Chunk[O] is a finite sequence of stream elements of type O stored inside a structure
    // optimized for indexed based lookup of elements.

    // We can create a stream directly through the Stream.chunk method, which accepts a sequence of Chunk
    println("\nStream of chunks")
    println("================\n")

    val chunkedAvengers: Chunk[Actor] = Chunk.array(Array(
      scarlettJohansson,
      robertDowneyJr,
      chrisEvans,
      markRuffalo,
      chrisHemsworth,
      jeremyRenner,
      tomHolland
    ))

    val avengerActors: Stream[Pure, Actor] = Stream.chunk(chunkedAvengers)
    println(s"The Avengers!\n\n${avengerActors.compile.toList.mkString("\n")}")

    // The fs2 library defines a lot of smart-constructors for the Chunk type, letting us create a
    // Chunk from an Option, a Seq, a Queue, and so on.
    //
    // Most of the functions defined on streams are Chunk- aware, so we don’t have to worry about
    // chunks while working with them.

    (justiceLeagueActors, avengerActors, spiderMenActors)
  }

  // (3) Transforming streams
  def transformingStreams(pureStreams: (Stream[Pure, Actor], Stream[Pure, Actor], Stream[Pure, Actor])): Unit = {
    println("\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
    println("> TRANSFORMING STREAMS               <")
    println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<")

    val (justiceLeagueActors, avengerActors, _) = pureStreams

    // concatenate two streams
    println("\nConcatenate 2 streams")
    println("=====================\n")
    val dcAndMarvelSuperheroes: Stream[Pure, Actor] = justiceLeagueActors ++ avengerActors
    println(s"DC and Marvel superheroes:\n\n${dcAndMarvelSuperheroes.compile.toList.mkString("\n")}")

    // The Stream type forms a monad on the O type parameter, which means that a flatMap method
    // is available on streams, and we can use it to concatenate operations concerning the
    // output values of the stream.

    // For example, printing to the console the elements of a stream uses the flatMap method
    //  def flatMap[F2[x] >: F[x], O2](f: O => Stream[F2, O2]): Stream[F2, O2]

    println("\nflatMap and eval a pure stream into an effectful stream")
    println("=======================================================\n")
    val printedJusticeLeagueActors: Stream[IO, Unit] = justiceLeagueActors.flatMap { actor =>
      Stream.eval(IO.println(actor))
    }
    printedJusticeLeagueActors.compile.drain.unsafeRunSync()

    // The pattern of calling the function Stream.eval inside a flatMap is so common that fs2
    // provides a shortcut for it through the evalMap method

    // def evalMap[F2[x] >: F[x], O2](f: O => F2[O2]): Stream[F2, O2]
    println("\nevalMap = flatMap + eval")
    println("========================\n")
    val evalMappedJusticeLeagueActors: Stream[IO, Unit] = justiceLeagueActors.evalMap(IO.println)
    evalMappedJusticeLeagueActors.compile.drain.unsafeRunSync()

    // If we need to perform some effects on the stream, but we don’t want to change the type
    // of the stream, we can use the evalTap method:
    println("\nevalTap evaluates effect without changing stream return type")
    println("============================================================\n")
    val evalTappedJusticeLeagueActors: Stream[IO, Actor] = justiceLeagueActors.evalTap(IO.println)
    evalTappedJusticeLeagueActors.compile.drain.unsafeRunSync()

    // An essential feature of fs2 streams is that their functions take constant time, regardless
    // of the structure of the stream itself. So, concatenating two streams is a constant time
    // operation, whether the streams contain many elements or are infinite. As we will see in
    // the rest of the article, this feature concerns the internal representation, which is
    // implemented as a pull-based structure.

    // group Avengers by actor's name
    // fold works as per usual

    println("\nUse fold to group actors by first name")
    println("======================================\n")
    val avengersActorsByFirstName: Stream[Pure, Map[String, List[Actor]]] = avengerActors.fold(Map.empty[String, List[Actor]]) { case (map, actor) =>
      map + (actor.firstName -> (actor :: map.getOrElse(actor.firstName, Nil)))
    }

    val groupedAvengers: cats.Id[List[Map[String, List[Actor]]]] = avengersActorsByFirstName.compile.toList
    groupedAvengers.foreach(m => m.foreach {
      case (name, actors) => println(s"$name => ${actors.mkString(",")}")
    })

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

    println("\nTransform Justice League actors into a stream containing only first name and surname")
    println("====================================================================================\n")
    val fromActorToNamePipe: Pipe[IO, Actor, String] = in =>
      in.map(actor => s"${actor.firstName} ${actor.lastName}")

    def toConsole[T]: Pipe[IO, T, Unit] = in =>
      in.evalMap(IO.println)

    // The justiceLeagueActors stream represents the source, whereas the fromActorToNamePipe represents a pipe,
    // and the toConsole represents the sink. We can conclude that a pipe is pretty much a map/flatMap
    // type functional operation, but the pipe concept fits nicely into the mental model of a Stream.
    val stringNamesOfActors: Stream[IO, Unit] = justiceLeagueActors.through(fromActorToNamePipe).through(toConsole)

    stringNamesOfActors.compile.drain.unsafeRunSync
  }

  // (4) Error Handling
  def errorHandling(justiceLeagueActors: Stream[Pure, Actor]): Stream[IO, Int] = {
    println("\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
    println("> ERROR HANDLING                     <")
    println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<")

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

    val savedJusticeLeagueActors: Stream[IO, Int] = justiceLeagueActors.evalMap(ActorRepository.save)

    // The stream is interrupted by the exception. Every time an exception occurs during pulling
    // elements from a stream, the stream execution ends.
    println("\nStream throws exception and stops (caught via Try)")
    println("==================================================\n")
    Try {
      savedJusticeLeagueActors.compile.drain.unsafeRunSync
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
    val errorHandledSavedJusticeLeagueActors: Stream[IO, AnyVal] = savedJusticeLeagueActors.handleErrorWith( // Stream[IO, Int]
      error => Stream.eval(IO.println(s"handleErrorWith Error: $error")) // Stream[IO, Unit]
    )

    println("\nStream throws exception, handled via handleErrorWith")
    println("====================================================\n")
    errorHandledSavedJusticeLeagueActors.compile.drain.unsafeRunSync

    // The attempt method works using the scala.Either type, which returns a stream of Either elements. The
    // resulting stream pulls elements wrapped in a Right instance until the first error occurs, which is
    // represented as an instance of a Throwable wrapped in a Left
    println("\nStream throws exception, handled via attempt")
    println("============================================\n")

    val attemptedSavedActors: Stream[IO, Either[Throwable, Int]] = savedJusticeLeagueActors.attempt

    attemptedSavedActors.evalMap {
      case Left(error) => IO.println(s"attempt Error: $error")
      case Right(id) => IO.println(s"Saved actor with id: $id")
    }.compile.drain.unsafeRunSync

    // attempt method is implemented internally using the handleErrorWith

    // fs2 library code
    // def attempt: Stream[F, Either[Throwable, O]] =
    //   map(Right(_): Either[Throwable, O])                // Stream[F, Either[Throwable, O]]
    //     .handleErrorWith(e => Stream.emit(Left(e)))

    savedJusticeLeagueActors
  }

  // (5) Resource Management
  def resourceManagement(savedJusticeLeagueActors: Stream[IO, Int]): Unit = {
    println("\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
    println("> RESOURCE MANAGEMENT                <")
    println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<")

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
    val managedJlActors: Stream[IO, Int] = Stream.bracket(acquire)(release).flatMap(conn => savedJusticeLeagueActors) // conn would be used in real-life code

    // Since savedJusticeLeagueActors throws an exception when the stream is evaluated, the managedJlActors stream will
    // terminate with an error. The release function is called, and the conn value is released

    println("\nStream throws exception and stops, bracket ensures stream is closed (exception caught via Try)")
    println("==============================================================================================\n")
    Try {
      managedJlActors.compile.drain.unsafeRunSync
    } match {
      case Failure(exception) => println(s"Exception: $exception")
      case _ => ()
    }
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

    val unconsAvengersActors: Pull[Pure, INothing, Option[(Chunk[Actor], Stream[Pure, Actor])]] =
      avengerActors.pull.uncons

    // Remember: we cannot call stream on unconsAvengersActors as R is not Unit; it is
    // Option[(Chunk[Actor], Stream[Pure, Actor])]

    // println(unconsAvengersActors.stream.compile.toList)

    // uncons1 is a variant of the uncons method; it returns the first stream element rather than the first Chunk
    val uncons1AvengersActors: Pull[Pure, INothing, Option[(Actor, Stream[Pure, Actor])]] =
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
    val pureStreams = buildingStreams

    val (justiceLeagueActors, avengerActors, spiderActors) = pureStreams

    transformingStreams(pureStreams)

    val savedJusticeLeagueActors = errorHandling(justiceLeagueActors)

    resourceManagement(savedJusticeLeagueActors)

    pullType(avengerActors)

    concurrentStreams(avengerActors, justiceLeagueActors, spiderActors)

    println("\nRunning the IOApp (the preferred way)")
    println("=====================================\n")

    // We can run the application as an IOApp (the preferred way)
    justiceLeagueActors.covary[IO].compile.drain.as(ExitCode.Success)
  }
}

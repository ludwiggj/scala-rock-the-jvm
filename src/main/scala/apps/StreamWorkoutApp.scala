package apps

import cats.Id
import cats.effect._
import cats.effect.unsafe.implicits.global
import fs2.Pure

// Very important to deal with arrays

object StreamWorkoutApp extends IOApp {

  // Basded on http://www.beyondthelines.net/programming/streaming-patterns-with-fs2/
  override def run(args: List[String]): IO[ExitCode] = {
    val stream: fs2.Stream[Pure, Int] = fs2.Stream(1, 2, 3, 4)
    val list: Id[List[Int]] = stream.compile.toList
    list.foreach(println)

    val stream2: fs2.Stream[IO, Int] = fs2.Stream.eval(IO(2))
    val list2: IO[List[Int]] = stream2.compile.toList
    list2.unsafeRunSync().foreach(println)

    val stream3: fs2.Stream[Pure, Char] = fs2.Stream.emits('A' to 'E')

    val stream4: fs2.Stream[Pure, IndexedSeq[String]] = stream3
      .map(letter => (1 to 3)
        .map(index => s"$letter$index"))
    stream4.compile.toList.foreach(println)

    val stream5: fs2.Stream[Pure, String] = stream4.flatMap(seq => fs2.Stream.emits(seq))
    stream5.compile.toList.foreach(println)

    IO(ExitCode.Success)
  }
}

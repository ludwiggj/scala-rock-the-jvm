package streams

import cats.effect.IO

object ActorRepository {
  case class MyException(msg: String) extends RuntimeException(msg)

  def save(actor: Actor): IO[Int] = IO {
    println(s"Saving actor: $actor")
    if ((actor.id + 1) % 4 == 0) {
      val exceptionMsg = s"Cannot save actor $actor - something went wrong during the communication with the persistence layer"
      println(exceptionMsg)
      throw MyException(exceptionMsg)
    }
    println(s"Saved actor $actor")
    actor.id
  }

  def save(dbc: DatabaseConnection, actor: Actor): IO[Int] =
    IO.println(s"Saving actor to $dbc") >> save(actor)
}
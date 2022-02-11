package apps

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import doobie.util.transactor.Transactor
import doobie.implicits._

object YoloApp extends App {
  val xa: Transactor[IO] = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql:myimdb",
    "postgres",
    "example"
  )

  val y = xa.yolo
  import y._

  val query = sql"select name from actors".query[String].to[List]
  query.quick.unsafeRunSync()
}

package newtypes

import cats.effect._
import doobie._
import doobie.implicits._
import ludwiggj.domain.Director
import ludwiggj.types.{ActorName, DirectorId, DirectorLastName, DirectorName}

object NewTypesDoobieApp extends IOApp {
  // implicit val actorNameGet: Get[ActorName] = Get[String].map(ActorName(_))
  // implicit val actorNamePut: Put[ActorName] = Put[String].contramap(_.value)

  // Do all in one using Meta
  implicit val actorNameMeta: Meta[ActorName] = Meta[String].imap(ActorName(_))(_.value)

  implicit val directorRead: Read[Director] =
    Read[(Int, String, String)].map { case (id, name, lastname) =>
      Director(DirectorId(id), DirectorName(name), DirectorLastName(lastname) )
    }

  implicit val directorWrite: Write[Director] =
    Write[(Int, String, String)].contramap(director => (director.id.id, director.name.name, director.lastName.lastName))

  val xa: Transactor[IO] = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql:myimdb",
    "postgres", // username
    "example" // password
  )

  def findAllActorNamesProgram(): IO[List[ActorName]] = {
    sql"select name from actors".query[ActorName].to[List].transact(xa)
  }

  def findAllDirectorsProgram(): IO[List[Director]] = {
    val findAllDirectors: fs2.Stream[doobie.ConnectionIO, Director] =
      sql"select id, name, last_name from directors".query[Director].stream
    findAllDirectors.compile.toList.transact(xa)
  }

  override def run(args: List[String]): IO[ExitCode] = {
      findAllActorNamesProgram()
        .map(println) >>
      findAllDirectorsProgram()
        .map(println)
        .as(ExitCode.Success)
  }
}

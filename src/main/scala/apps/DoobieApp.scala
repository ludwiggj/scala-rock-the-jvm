package apps

import cats.data.NonEmptyList
import cats.effect._
import cats.implicits._
import domain.{Actor, Movie}
import doobie._
import doobie.implicits._
import java.util.UUID

// Very important to deal with arrays
import doobie.postgres._
import doobie.postgres.implicits._

object DoobieApp extends IOApp {
  val xa: Transactor[IO] = Transactor.fromDriverManager[IO](
    "org.postgresql.Driver",
    "jdbc:postgresql:myimdb",
    "postgres", // username
    "example" // password
  )

  def findAllActorsNamesProgram: IO[List[String]] = {
    val findAllActorsQuery: doobie.Query0[String] = sql"select name from actors".query[String]
    val findAllActors: doobie.ConnectionIO[List[String]] = findAllActorsQuery.to[List]
    findAllActors.transact(xa)
  }

  def findActorByIdProgram(id: Int): IO[Actor] = {
    val findActorById: doobie.ConnectionIO[Actor] =
      sql"select id, name from actors where id = $id".query[Actor].unique
    findActorById.transact(xa)
  }

  def findActorByIdSafelyProgram(id: Int): IO[Option[Actor]] = {
    val findActorById: doobie.ConnectionIO[Option[Actor]] =
      sql"select id, name from actors where id = $id".query[Actor].option
    findActorById.transact(xa)
  }

  // Streaming the result
  val actorsNamesStream: fs2.Stream[doobie.ConnectionIO, String] =
    sql"select name from actors".query[String].stream

  val actorsNamesList: IO[List[String]] = actorsNamesStream.compile.toList.transact(xa)

  def findAllActorsIdsAndNamesProgram: IO[List[(Int, String)]] = {
    val query: doobie.Query0[(Int, String)] = sql"select id, name from actors".query[(Int, String)]
    val findAllActors: doobie.ConnectionIO[List[(Int, String)]] = query.to[List]
    findAllActors.transact(xa)
  }

  def findAllActorsProgram: IO[List[Actor]] = {
    val findAllActors: fs2.Stream[doobie.ConnectionIO, Actor] =
      sql"select id, name from actors".query[Actor].stream
    findAllActors.compile.toList.transact(xa)
  }

  def findActorsByNameInitialLetterProgram(initialLetter: String): IO[List[Actor]] = {
    val findActors: fs2.Stream[doobie.ConnectionIO, Actor] =
      sql"select id, name from actors where LEFT(name, 1) = $initialLetter".query[Actor].stream
    findActors.compile.toList.transact(xa)
  }

  def findActorsByInitialLetterUsingFragmentsProgram(initialLetter: String): IO[List[Actor]] = {
    val select: Fragment = fr"select id, name"
    val from: Fragment = fr"from actors"
    val where: Fragment = fr"where LEFT(name, 1) = $initialLetter"

    val statement = select ++ from ++ where

    statement.query[Actor].stream.compile.toList.transact(xa)
  }

  def findActorsByInitialLetterUsingFragmentsAndMonoidsProgram(initialLetter: String): IO[List[Actor]] = {
    import cats.syntax.monoid._

    val select: Fragment = fr"select id, name"
    val from: Fragment = fr"from actors"
    val where: Fragment = fr"where LEFT(name, 1) = $initialLetter"

    val statement = select |+| from |+| where

    statement.query[Actor].stream.compile.toList.transact(xa)
  }

  def findActorsByNamesProgram(actorNames: NonEmptyList[String]): IO[List[Actor]] = {
    val sqlStatement: Fragment =
      fr"select id, name from actors where " ++ Fragments.in(fr"name", actorNames) // name IN (...)

    sqlStatement.query[Actor].stream.compile.toList.transact(xa)
  }

  def saveActorProgram(name: String): IO[Int] = {
    val saveActor: doobie.ConnectionIO[Int] =
      sql"insert into actors (name) values ($name)".update.run
    saveActor.transact(xa)
  }

  def saveActorAndGetIdProgram(name: String): IO[Int] = {
    val saveActor: doobie.ConnectionIO[Int] =
      sql"insert into actors (name) values ($name)"
        .update.withUniqueGeneratedKeys[Int]("id")
    saveActor.transact(xa)
  }

  def saveAndGetActorProgram(name: String): IO[Actor] = {
    val retrievedActor = for {
      id <- sql"insert into actors (name) values ($name)".update.withUniqueGeneratedKeys[Int]("id")
      actor <- sql"select * from actors where id = $id".query[Actor].unique
    } yield actor
    retrievedActor.transact(xa)
  }

  def saveActorsProgram(actors: NonEmptyList[String]): IO[Int] = {
    val insertStmt: String = "insert into actors (name) values (?)"
    val numberOfRows: doobie.ConnectionIO[Int] = Update[String](insertStmt).updateMany(actors.toList)
    numberOfRows.transact(xa)
  }

  def saveActorsAndReturnThemProgram(actors: NonEmptyList[String]): IO[List[Actor]] = {
    val insertStmt: String = "insert into actors (name) values (?)"
    val actorsIds = Update[String](insertStmt).updateManyWithGeneratedKeys[Actor]("id", "name")(actors.toList)
    actorsIds.compile.toList.transact(xa)
  }

  def findAllMoviesProgram: IO[List[(String, String)]] =
    sql"select title, year_of_production from movies".query[(String, String)].to[List].transact(xa)

  def updateJLYearOfProductionProgram: IO[Int] = {
    val year = 2029
    val id = "5e5a39bb-a497-4432-93e8-7322f16ac0b2"
    sql"update movies set year_of_production = $year where id::text = $id".update.run.transact(xa)
  }

  def findMovieByNameProgram(movieName: String): IO[Option[Movie]] = {
    val query = sql"""
                     |SELECT m.id,
                     |       m.title,
                     |       m.year_of_production,
                     |       array_agg(a.name) as actors,
                     |       d.name || ' ' || d.last_name
                     |FROM movies m
                     |JOIN movies_actors ma ON m.id = ma.movie_id
                     |JOIN actors a ON ma.actor_id = a.id
                     |JOIN directors d ON m.director_id = d.id
                     |WHERE m.title = $movieName
                     |GROUP BY (m.id,
                     |          m.title,
                     |          m.year_of_production,
                     |          d.name,
                     |          d.last_name)
                     |""".stripMargin
      .query[Movie]
      .option
    query.transact(xa)
  }

  def findMovieByNameWithoutSqlJoinProgram(movieName: String): IO[Option[Movie]] = {

    def findMovieByTitle(): doobie.ConnectionIO[Option[(UUID, String, Int, Int)]] =
      sql"""
           | select id, title, year_of_production, director_id
           | from movies
           | where title = $movieName""".stripMargin
        .query[(UUID, String, Int, Int)].option

    def findDirectorById(directorId: Int): doobie.ConnectionIO[List[(String, String)]] =
      sql"select name, last_name from directors where id = $directorId"
        .query[(String, String)].to[List]

    def findActorsByMovieId(movieId: UUID): doobie.ConnectionIO[List[String]] =
      sql"""
           | select a.name
           | from actors a
           | join movies_actors ma on a.id = ma.actor_id
           | where ma.movie_id = $movieId
           |""".stripMargin
        .query[String]
        .to[List]

    val query = for {
      maybeMovie <- findMovieByTitle()
      directors <- maybeMovie match {
        case Some((_, _, _, directorId)) => findDirectorById(directorId)
        case None => List.empty[(String, String)].pure[ConnectionIO]
      }
      actors <- maybeMovie match {
        case Some((movieId, _, _, _)) => findActorsByMovieId(movieId)
        case None => List.empty[String].pure[ConnectionIO]
      }
    } yield {
      maybeMovie.map { case (id, title, year, _) =>
        val directorName = directors.head._1
        val directorLastName = directors.head._2
        Movie(id.toString, title, year, actors, s"$directorName $directorLastName")
      }
    }
    query.transact(xa)
  }

  // findAllMoviesUngrouped()
//    val query = sql"""
//                     |SELECT m.id,
//                     |       m.title,
//                     |       m.year_of_production,
//                     |       a.name as actors,
//                     |       d.name || ' ' || d.last_name
//                     |FROM movies m
//                     |JOIN movies_actors ma ON m.id = ma.movie_id
//                     |JOIN actors a ON ma.actor_id = a.id
//                     |JOIN directors d ON m.director_id = d.id
//                     |GROUP BY (m.id,
//                     |          m.title,
//                     |          m.year_of_production,
//                     |          a.name,
//                     |          d.name,
//                     |          d.last_name)
//                     |""".stripMargin

  // https://www.postgresqltutorial.com/postgresql-aggregate-functions/postgresql-array_agg-function/
  def findAllMovies(): IO[List[Movie]] = {
    val query = sql"""
                     |SELECT m.id,
                     |       m.title,
                     |       m.year_of_production,
                     |       array_agg(a.name) as actors,
                     |       d.name || ' ' || d.last_name
                     |FROM movies m
                     |JOIN movies_actors ma ON m.id = ma.movie_id
                     |JOIN actors a ON ma.actor_id = a.id
                     |JOIN directors d ON m.director_id = d.id
                     |GROUP BY (m.id,
                     |          m.title,
                     |          m.year_of_production,
                     |          d.name,
                     |          d.last_name)
                     |""".stripMargin
      .query[Movie]

    query.stream.compile.toList.transact(xa)
  }



  override def run(args: List[String]): IO[ExitCode] = {
    //    saveActorProgram("Brian Blessed")
    //      .map(println) >>
    //    saveActorAndGetIdProgram("Bobby Dazzler")
    //      .map(println) >>
    //    saveAndGetActorProgram("Brie Larsen")
    //      .map(println) >>
    //    saveActorsProgram(NonEmptyList("Zippy", List("George", "Bungle")))
    //      .map(println) >>

    saveActorsAndReturnThemProgram(NonEmptyList("Jennifer Lopez", List("Dolly Parton")))
      .map(println) >>
      findAllActorsNamesProgram
        .map(println) >>
      findActorByIdProgram(1)
        .map(println) >>
      findActorByIdSafelyProgram(1)
        .map(println) >>
      findActorByIdSafelyProgram(0)
        .map(println) >>
      actorsNamesList
        .map(println) >>
      findAllActorsIdsAndNamesProgram
        .map(println) >>
      findAllActorsProgram
        .map(println) >>
      findActorsByNameInitialLetterProgram("R")
        .map(println) >>
      findActorsByInitialLetterUsingFragmentsProgram("G")
        .map(println) >>
      findActorsByInitialLetterUsingFragmentsAndMonoidsProgram("J")
        .map(println) >>
      findActorsByNamesProgram(NonEmptyList("Ezra Miller", List("Matt Damon", "Ben Affleck")))
        .map(println) >>
      findAllMoviesProgram
        .map(println) >>
      updateJLYearOfProductionProgram
        .map(println) >>
      findMovieByNameProgram("Zack Snyder's Justice League")
        .map(println) >>
      findMovieByNameWithoutSqlJoinProgram("Zack Snyder's Justice League")
        .map(println) >>
      findAllMovies()
        .map(println)
        .as(ExitCode.Success)
  }
}

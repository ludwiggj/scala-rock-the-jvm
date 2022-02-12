package tagless

import cats.effect.{MonadCancelThrow, Resource}
import domain.DirectorName
import doobie.implicits.toSqlInterpolator
import doobie.implicits._
import doobie.util.transactor.Transactor

trait Directors[F[_]] {
  def findById(id: Int): F[Option[DirectorName]]
  def findAll: F[List[DirectorName]]
  def create(name: String, lastName: String): F[Int]
}

object Directors {
  def make[F[_]: MonadCancelThrow](postgres: Resource[F, Transactor[F]]): Directors[F] = {
    new Directors[F] {
      //import DirectorSQL._

      def findById(id: Int): F[Option[DirectorName]] =
        postgres.use { xa =>
          sql"SELECT name, last_name FROM directors WHERE id = $id".query[DirectorName].option.transact(xa)
        }

      def findAll: F[List[DirectorName]] =
        postgres.use { xa =>
          sql"SELECT name, last_name FROM directors".query[DirectorName].to[List].transact(xa)
        }

      def create(name: String, lastName: String): F[Int] =
        postgres.use { xa =>
          sql"INSERT INTO directors (name, last_name) VALUES ($name, $lastName)".update.withUniqueGeneratedKeys[Int]("id").transact(xa)
        }
    }
  }
}
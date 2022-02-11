package tagless

import cats.effect.{MonadCancelThrow, Resource}
import domain.Director
import doobie.implicits.toSqlInterpolator
import doobie.implicits._
import doobie.util.transactor.Transactor

trait Directors[F[_]] {
  def findById(id: Int): F[Option[Director]]
  def findAll: F[List[Director]]
  def create(name: String, lastName: String): F[Int]
}

object Directors {
  def make[F[_]: MonadCancelThrow](postgres: Resource[F, Transactor[F]]): Directors[F] = {
    new Directors[F] {
      //import DirectorSQL._

      def findById(id: Int): F[Option[Director]] =
        postgres.use { xa =>
          sql"SELECT name, last_name FROM directors WHERE id = $id".query[Director].option.transact(xa)
        }

      def findAll: F[List[Director]] =
        postgres.use { xa =>
          sql"SELECT name, last_name FROM directors".query[Director].to[List].transact(xa)
        }

      def create(name: String, lastName: String): F[Int] =
        postgres.use { xa =>
          sql"INSERT INTO directors (name, last_name) VALUES ($name, $lastName)".update.withUniqueGeneratedKeys[Int]("id").transact(xa)
        }
    }
  }
}
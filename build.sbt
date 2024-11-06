ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.14"

val DoobieVersion = "1.0.0-RC1"
val NewTypeVersion = "0.4.4"
val Fs2Version = "3.10.2"

lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "org.tpolecat" %% "doobie-core" % DoobieVersion,
    "org.tpolecat" %% "doobie-postgres" % DoobieVersion,
    "org.tpolecat" %% "doobie-hikari" % DoobieVersion,
    "io.estatico" %% "newtype" % NewTypeVersion,
    "co.fs2" %% "fs2-core" % Fs2Version,
    "org.scalameta" %% "munit" % "1.0.2" % Test,
    "org.typelevel" %% "munit-cats-effect" % "2.0.0" % Test
  ))

lazy val root = (project in file("."))
  .settings(
    name := "scala-rock-the-jvm",
    commonSettings
  )

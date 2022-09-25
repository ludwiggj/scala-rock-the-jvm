ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

val DoobieVersion = "1.0.0-RC2"
val NewTypeVersion = "0.4.4"
val Fs2Version = "3.3.0"
val Http4sVersion = "1.0.0-M36"
val CirceVersion = "0.14.3"
val catsVersion = "2.8.0"
val catsEffectVersion = "3.2.0"

lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
    "org.tpolecat" %% "doobie-core" % DoobieVersion withSources(),
    "org.tpolecat" %% "doobie-postgres" % DoobieVersion withSources(),
    "org.tpolecat" %% "doobie-hikari" % DoobieVersion withSources(),
    "io.estatico" %% "newtype" % NewTypeVersion,
    "co.fs2" %% "fs2-core" % Fs2Version withSources(),
    "org.http4s" %% "http4s-blaze-server" % Http4sVersion withSources(),
    "org.http4s" %% "http4s-circe" % Http4sVersion withSources(),
    "org.http4s" %% "http4s-dsl" % Http4sVersion withSources(),
    "io.circe" %% "circe-generic" % CirceVersion withSources(),
    "org.typelevel" %% "cats-core" % catsVersion withSources(),
    "org.typelevel" %% "cats-effect" % "3.3.14" withSources()
  ))

lazy val root = (project in file("."))
  .settings(
    name := "scala-rock-the-jvm",
    commonSettings
  )

addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.2" cross CrossVersion.full)
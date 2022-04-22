ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.8"

val DoobieVersion = "1.0.0-RC1"
val NewTypeVersion = "0.4.4"
val Fs2Version = "3.2.4"

lazy val commonSettings = Seq(
  libraryDependencies ++= Seq(
  "org.tpolecat" %% "doobie-core"     % DoobieVersion,
  "org.tpolecat" %% "doobie-postgres" % DoobieVersion,
  "org.tpolecat" %% "doobie-hikari"   % DoobieVersion,
  "io.estatico"  %% "newtype"         % NewTypeVersion,
  "co.fs2"       %% "fs2-core"        % Fs2Version
))

lazy val root = (project in file("."))
  .settings(
    name := "doobie-rockthejvm",
    commonSettings
  )
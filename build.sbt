name := "wts"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  specs2 % Test,
  "com.typesafe.play" %% "play-mailer" % "3.0.0-M1", // Mail Client :    https://github.com/playframework/play-mailer
  "jp.t2v" %% "play2-auth"        % "0.14.1",        // Authentication : https://github.com/t2v/play2-auth
  play.sbt.Play.autoImport.cache
)

resolvers ++= Seq(
  "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
)

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator

fork in run := true

scalacOptions += "-feature"

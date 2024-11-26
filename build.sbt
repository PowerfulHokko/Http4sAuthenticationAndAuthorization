import scala.collection.Seq

ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.6.1"
val http4sVersion = "1.0.0-M41"

lazy val root = (project in file("."))
  .settings(
    name := "Http4sAuthenticationAndAuthorization",
    idePackagePrefix := Some("com.hokko")
  )

//config
libraryDependencies += "com.github.pureconfig" %% "pureconfig-cats" % "0.17.7"

//http4s
libraryDependencies ++= Seq(
  "org.http4s" %% "http4s-ember-client" % http4sVersion,
  "org.http4s" %% "http4s-ember-server" % http4sVersion,
  "org.http4s" %% "http4s-dsl"          % http4sVersion,
  "org.http4s" %% "http4s-server" % http4sVersion
)

//logger
libraryDependencies ++= Seq(
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
  "ch.qos.logback" % "logback-classic" % "1.5.6" exclude("org.slf4j", "slf4j-simple"),
  "org.typelevel" % "log4cats-core_3" % "2.7.0" exclude("org.slf4j", "slf4j-simple"),
  "org.typelevel" % "log4cats-slf4j_3" % "2.7.0" exclude("org.slf4j", "slf4j-simple")
)
//libraryDependencies ++= Seq(
//  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.5",
//  "ch.qos.logback" % "logback-classic" % "1.5.6" exclude("org.slf4j", "slf4j-simple")
//)
//
//libraryDependencies ++= Seq(
//  "org.typelevel" % "log4cats-core_3" % "2.7.0", // Adjust the version as needed
//  "org.typelevel" % "log4cats-slf4j_3" % "2.7.0", // Adjust the version as needed
////  "org.slf4j" % "slf4j-api" % "2.0.12", // Adjust the version as needed
////  "org.slf4j" % "slf4j-simple" % "2.0.13" // Optional, for a simple SLF4J binding
//)

//cats
libraryDependencies += ("org.typelevel" %% "cats-core" % "2.12.0")
libraryDependencies += ("org.typelevel" %% "cats-effect" % "3.5.4")

//JSON
libraryDependencies += "org.http4s" %% "http4s-circe" % http4sVersion
// Optional for auto-derivation of JSON codecs
libraryDependencies += "io.circe" %% "circe-generic" % "0.14.7"
// Optional for string interpolation to JSON model
libraryDependencies += "io.circe" %% "circe-literal" % "0.14.7"
libraryDependencies +="io.circe" %% "circe-parser" % "0.14.5"

// https://mvnrepository.com/artifact/commons-codec/commons-codec
libraryDependencies += "commons-codec" % "commons-codec" % "1.17.0"

//db

//test
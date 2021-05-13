name := """s3-example"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.13.6"

val akkaHttpVersion = "10.1.14"

libraryDependencies ++= Seq(
    guice,
    caffeine,

    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-xml" % akkaHttpVersion,
    "com.lightbend.akka" %% "akka-stream-alpakka-s3" % "2.0.2",
)

PlayKeys.externalizeResources := false

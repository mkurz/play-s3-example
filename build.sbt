name := """s3-example"""
organization := "com.example"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayJava)

scalaVersion := "2.12.10"

val akkaVersion = "2.5.32"
val akkaHttpVersion = "10.1.12"

libraryDependencies ++= Seq(
    guice,
    // javaJdbc,
    // evolutions,
    caffeine,

    "com.typesafe.akka" %% "akka-actor"  % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j"  % akkaVersion,

    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-xml" % akkaHttpVersion,
    "com.lightbend.akka" %% "akka-stream-alpakka-s3" % "2.0.2",

    // "com.h2database" % "h2" % "1.4.199",
    // "org.mybatis" % "mybatis" % "3.5.2",
    // "org.mybatis" % "mybatis-guice" % "3.10",
    // "com.google.inject.extensions" % "guice-multibindings" % "4.2.2"
)

PlayKeys.externalizeResources := false

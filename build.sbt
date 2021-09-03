name := "akka-iot-tutorial"

version := "0.1"

scalaVersion := "2.13.6"

val AkkaVersion = "2.6.16"

libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.5"
libraryDependencies += "com.typesafe.akka" %% "akka-actor-testkit-typed" % AkkaVersion % Test
libraryDependencies += "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion
libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.9" % Test

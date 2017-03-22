name := "akka-seed"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.4.17",
  "com.typesafe.akka" %% "akka-slf4j" % "2.4.17",
  "org.slf4j" % "log4j-over-slf4j" % "1.7.22",
  "ch.qos.logback" % "logback-classic" % "1.2.2"
)
        
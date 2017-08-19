lazy val commonSettings = Seq(
  organization := "com.docurated",
  scalaVersion := "2.11.8",
  resolvers ++= Seq(
    "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots",
    "clojars" at "https://clojars.org/repo"
  )
)

lazy val root = (project in file(".")).
  settings(
    commonSettings,
    name := "Cluster Emcee",
    version := "0.2",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "ch.qos.logback" % "logback-core" % "1.2.3",
      "com.amazonaws" % "aws-java-sdk" % "1.9.38",
      "com.lightbend.akka" %% "akka-management-cluster-http" % "0.3",
      "com.typesafe.akka" %% "akka-actor" % "2.5.1",
      "com.typesafe.akka" %% "akka-remote" % "2.5.1",
      "com.typesafe.akka" %% "akka-slf4j" % "2.5.1",
      "com.typesafe.akka" %% "akka-http" % "10.0.6",
      "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.6",
      "com.typesafe.akka" %% "akka-cluster" % "2.5.1",
      "com.typesafe.akka" %% "akka-cluster-tools" % "2.5.1",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
      "de.aktey.akka.k8s" %% "seednode-config" % "1.0.1",
      "io.riemann" % "riemann-java-client" % "0.4.5",
      "org.json4s" %% "json4s-native" % "3.5.0",
      "org.apache.commons" % "commons-collections4" % "4.1",
      "org.apache.commons" % "commons-lang3" % "3.3.2",
      "org.json4s" %% "json4s-native" % "3.5.0",
      "org.json4s" %% "json4s-jackson" % "3.5.0",
      "org.json4s" %% "json4s-ext" % "3.5.0",
      "org.slf4j" % "slf4j-api" % "1.7.25",
      "net.databinder.dispatch" %% "dispatch-core" % "0.11.3",
      "net.databinder.dispatch" %% "dispatch-lift-json" % "0.11.3",
      "org.mockito" % "mockito-all" % "1.10.7" % "test",
      "org.scalatest" %% "scalatest" % "2.2.4" % "test"
    )
  )

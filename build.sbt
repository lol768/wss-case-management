import warwick.Gulp
import warwick.Testing._

organization := "uk.ac.warwick"
name := """case-management"""

version := "1.0-SNAPSHOT"

scalaVersion := "2.12.6"

javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

scalacOptions ++= Seq(
  "-encoding", "UTF-8", // yes, this is 2 args
  "-target:jvm-1.8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Yno-adapted-args",
  "-Ywarn-numeric-widen",
  "-Xfatal-warnings",
  "-Xsource:2.13"
)
scalacOptions in Test ++= Seq("-Yrangepos")
scalacOptions in (Compile, doc) ++= Seq("-no-link-warnings")

autoAPIMappings := true

// Avoid some of the constant SBT "Updating"
updateOptions := updateOptions.value.withCachedResolution(true)

lazy val main = (project in file("."))
  .enablePlugins(WarwickProject, PlayScala)
  .settings(
    packageZipTarball in Universal := (packageZipTarball in Universal).dependsOn(webpack).value
  )

val playUtilsVersion = "1.16"
val ssoClientVersion = "2.48"
val enumeratumVersion = "1.5.13"
val enumeratumSlickVersion = "1.5.15"

val appDeps = Seq(
  guice,
  ws,
  cacheApi,
  filters,

  // v3.0.0 is Play 2.6.x and Slick 3.1.x
  "com.typesafe.play" %% "play-slick" % "3.0.3",
  "com.typesafe.play" %% "play-slick-evolutions" % "3.0.3",

  "com.typesafe.slick" %% "slick" % "3.2.3",

  "com.typesafe.akka" %% "akka-cluster" % "2.5.11",
  "com.typesafe.akka" %% "akka-cluster-tools" % "2.5.11",

  "org.postgresql" % "postgresql" % "42.2.4",

  // in-memory JNDI context used by Play to pass DataSource to Quartz
  "tyrex" % "tyrex" % "1.0.1",
  "org.quartz-scheduler" % "quartz" % "2.3.0",

  "net.codingwell" %% "scala-guice" % "4.1.0",
  "com.google.inject.extensions" % "guice-multibindings" % "4.1.0",
  "com.adrianhurt" %% "play-bootstrap" % "1.2-P26-B3",

  "uk.ac.warwick.sso" %% "sso-client-play" % ssoClientVersion,

  "uk.ac.warwick.play-utils" %% "accesslog" % playUtilsVersion,
  "uk.ac.warwick.play-utils" %% "objectstore" % playUtilsVersion,
  "uk.ac.warwick.play-utils" %% "slick" % playUtilsVersion,

  "com.github.mumoshu" %% "play2-memcached-play26" % "0.9.3-warwick",

  "com.beachape" %% "enumeratum" % enumeratumVersion,
  "com.beachape" %% "enumeratum-play" % enumeratumVersion,
  "com.beachape" %% "enumeratum-play-json" % enumeratumVersion,
  "com.beachape" %% "enumeratum-slick" % enumeratumSlickVersion,

  "org.apache.jclouds.api" % "filesystem" % "2.1.0"
)

val testDeps = Seq(
  "org.mockito" % "mockito-all" % "1.10.19",
  "org.scalatest" %% "scalatest" % "3.0.3",
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.0",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.19",
  "uk.ac.warwick.sso" %% "sso-client-play-testing" % ssoClientVersion,
  "org.seleniumhq.selenium" % "selenium-java" % "3.14.0",
  "org.seleniumhq.selenium" % "selenium-chrome-driver" % "3.14.0",
  "com.h2database" % "h2" % "1.4.196"
).map(_ % Test)

libraryDependencies ++= (appDeps ++ testDeps).map(_.excludeAll(
  ExclusionRule(organization = "commons-logging"),
  // ehcache renamed ehcache-core, don't load in the old version
  ExclusionRule(organization = "net.sf.ehcache", name = "ehcache")
))

libraryDependencies += specs2 % Test

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator

// https://bugs.elab.warwick.ac.uk/browse/SSO-1653
dependencyOverrides += "xml-apis" % "xml-apis" % "1.4.01"

// Because jclouds is terrible
dependencyOverrides += "com.google.guava" % "guava" % "20.0"

// Because jclouds is terrible
dependencyOverrides += "com.google.code.gson" % "gson" % "2.5"

// Fix a dependency warning
dependencyOverrides += "org.json" % "json" % "20171018"

// Make built output available as Play assets.
unmanagedResourceDirectories in Assets += baseDirectory.value / "target/assets"

resolvers += Resolver.mavenLocal
resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
resolvers += "oauth" at "http://oauth.googlecode.com/svn/code/maven"
resolvers += "softprops-maven" at "http://dl.bintray.com/content/softprops/maven"
resolvers += "slack-client" at "https://mvnrepository.com/artifact/net.gpedro.integrations.slack/slack-webhook"
resolvers += "SBT plugins" at "https://repo.scala-sbt.org/scalasbt/sbt-plugin-releases/"
resolvers += "nexus" at "https://mvn.elab.warwick.ac.uk/nexus/content/groups/public"
credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")

// Define a special test task which does not fail when any test fails, so sequential tasks will be performed no
// matter the test result.
lazy val bambooTest = taskKey[Unit]("Run tests for CI")

bambooTest := {
  // Capture the test result
  val testResult = (test in Test).result.dependsOn(dependencyCheck).value
}

dependencyCheckFailBuildOnCVSS := 5
dependencyCheckSuppressionFiles := Seq(baseDirectory.value / "dependency-check-suppressions.xml")

// Webpack task

import scala.sys.process.Process

lazy val webpack = taskKey[Unit]("Run webpack when packaging the application")

def runAudit(file: File): Int = Process("npm audit", file).!
def runWebpack(file: File): Int = Process("npm run build", file).!

webpack := {
  if (runAudit(baseDirectory.value) != 0) throw new Exception("Some npm dependencies have problems that need fixing.")
  if (runWebpack(baseDirectory.value) != 0) throw new Exception("Something went wrong when running webpack.")
}

// Generate a new AES key for object store encryption
lazy val newEncryptionKey = taskKey[Unit]("Generate and print a new encryption key")
newEncryptionKey := println(EncryptionKey.generate())

runner := runner.dependsOn(webpack).value
dist := dist.dependsOn(webpack).value
stage := stage.dependsOn(webpack).value

import warwick.Gulp
import warwick.Testing._

name := """play-app-template"""

version := "1.0-SNAPSHOT"

scalaVersion := Common.scalaVersion

// ULTRAVIOLENCE
scalacOptions ++= Seq("-language:implicitConversions", "-unchecked", "-deprecation", "-feature", "-Xfatal-warnings")
scalacOptions ++= Seq("-feature")

// Avoid some of the constant SBT "Updating"
updateOptions := updateOptions.value.withCachedResolution(true)

lazy val root = (project in file(".")).enablePlugins(WarwickProject, PlayScala)
  .settings(
    Gulp.settings,
    // Package up assets before we build tar.gz
    packageZipTarball in Universal <<= (packageZipTarball in Universal).dependsOn(Gulp.gulpAssets)
  )

val appDeps = Seq(
  guice,
  ws,
  cacheApi,
  filters,

  // v3.0.0 is Play 2.6.x and Slick 3.1.x
  "com.typesafe.play" %% "play-slick" % "3.0.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "3.0.0",

  "com.typesafe.slick" %% "slick" % "3.2.0",

  "com.typesafe.akka" %% "akka-cluster" % "2.5.3",
  "com.typesafe.akka" %% "akka-cluster-tools" % "2.5.3",

  "com.oracle" % "ojdbc7" % "12.1.0.2.0",
  "com.h2database" % "h2" % "1.4.196", // For testing only

  "com.google.inject.extensions" % "guice-multibindings" % "4.1.0",
  "com.adrianhurt" %% "play-bootstrap" % "1.2-P26-B3",
  "uk.ac.warwick.sso" %% "sso-client-play" % "2.34",
  "uk.ac.warwick.play-utils" %% "accesslog" % "1.8"
)

val testDeps = Seq(
  "org.mockito" % "mockito-all" % "1.10.19",
  "org.scalatest" %% "scalatest" % "3.0.3",
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.0",
  "com.typesafe.akka" %% "akka-testkit" % "2.4.19",
  "uk.ac.warwick.sso" %% "sso-client-play-testing" % "2.34"
//  "com.h2database" % "h2" % "1.4.196"
).map(_ % Test)

libraryDependencies ++= (appDeps ++ testDeps).map(_.excludeAll(
  ExclusionRule(organization = "commons-logging")
))

libraryDependencies += specs2 % Test

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator

// https://bugs.elab.warwick.ac.uk/browse/SSO-1653
dependencyOverrides += "xml-apis" % "xml-apis" % "1.4.01"

// Make gulp output available as Play assets.
unmanagedResourceDirectories in Assets <+= baseDirectory { _ / "target" / "gulp" }

resolvers += ("Local Maven Repository" at "file:///" + Path.userHome.absolutePath + "/.m2/repository")
resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
resolvers += "oauth" at "http://oauth.googlecode.com/svn/code/maven"
resolvers += "softprops-maven" at "http://dl.bintray.com/content/softprops/maven"
resolvers += "slack-client" at "https://mvnrepository.com/artifact/net.gpedro.integrations.slack/slack-webhook"

// code coverage settings (run jacoco:cover)
jacoco.settings

parallelExecution in jacoco.Config := false

// Define a special test task which does not fail when any test fails, so sequential tasks will be performed no
// matter the test result.
lazy val bambooTest = taskKey[Unit]("Run tests for CI")

bambooTest := {
  // Capture the test result
  val testResult = (test in Test).result.value
}

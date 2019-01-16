organization := "uk.ac.warwick"
name := """case-management"""

version := "1.0-SNAPSHOT"

scalaVersion := "2.12.8"

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
    packageZipTarball in Universal := (packageZipTarball in Universal).dependsOn(webpack).value,
    javaOptions in Test += "-Dlogger.resource=test-logging.xml"
  )

val playUtilsVersion = "1.26"
val ssoClientVersion = "2.60.1"
val warwickUtilsVersion = "20181130"
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
  "com.typesafe.play" %% "play-mailer" % "6.0.1",
  "com.typesafe.play" %% "play-mailer-guice" % "6.0.1",

  "com.typesafe.slick" %% "slick" % "3.2.3",
  "org.postgresql" % "postgresql" % "42.2.5",
  "com.github.tminglei" %% "slick-pg" % "0.17.0",

  // in-memory JNDI context used by Play to pass DataSource to Quartz
  "tyrex" % "tyrex" % "1.0.1",
  "org.quartz-scheduler" % "quartz" % "2.3.0",

  "net.codingwell" %% "scala-guice" % "4.2.1",
  "com.google.inject.extensions" % "guice-multibindings" % "4.2.2",
  "com.adrianhurt" %% "play-bootstrap" % "1.2-P26-B3",

  "uk.ac.warwick.sso" %% "sso-client-play" % ssoClientVersion,

  "uk.ac.warwick.play-utils" %% "accesslog" % playUtilsVersion,
  "uk.ac.warwick.play-utils" %% "healthcheck" % playUtilsVersion,
  "uk.ac.warwick.play-utils" %% "objectstore" % playUtilsVersion,
  "uk.ac.warwick.play-utils" %% "office365" % playUtilsVersion,
  "uk.ac.warwick.play-utils" %% "slick" % playUtilsVersion,

  "uk.ac.warwick.util" % "warwickutils-core" % warwickUtilsVersion,
  "uk.ac.warwick.util" % "warwickutils-mywarwick" % warwickUtilsVersion exclude("uk.ac.warwick.sso", "sso-client"),
  "uk.ac.warwick.util" % "warwickutils-service" % warwickUtilsVersion,
  "uk.ac.warwick.util" % "warwickutils-virusscan" % warwickUtilsVersion,

  "com.github.mumoshu" %% "play2-memcached-play26" % "0.9.3",

  "com.beachape" %% "enumeratum" % enumeratumVersion,
  "com.beachape" %% "enumeratum-play" % enumeratumVersion,
  "com.beachape" %% "enumeratum-play-json" % enumeratumVersion,
  "com.beachape" %% "enumeratum-slick" % enumeratumSlickVersion,

  "org.apache.jclouds.api" % "filesystem" % "2.1.1",

  "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0",

  "org.dom4j" % "dom4j" % "2.1.1",
  "org.apache.tika" % "tika-core" % "1.20",
  "org.apache.tika" % "tika-parsers" % "1.20",

  "org.apache.poi" % "poi" % "4.0.1",
  "org.apache.poi" % "poi-ooxml" % "4.0.1",
  "org.apache.poi" % "poi-ooxml-schemas" % "4.0.1",

  "org.slf4j" % "log4j-over-slf4j" % "1.7.25"
)

val testDeps = Seq(
  "org.mockito" % "mockito-all" % "1.10.19",
  "org.scalatest" %% "scalatest" % "3.0.5",
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.18",
  "uk.ac.warwick.sso" %% "sso-client-play-testing" % ssoClientVersion,
  "org.seleniumhq.selenium" % "selenium-java" % "3.141.59",
  "org.seleniumhq.selenium" % "selenium-chrome-driver" % "3.141.59",
  "com.opentable.components" % "otj-pg-embedded" % "0.12.5",
  "net.sourceforge.htmlcleaner" % "htmlcleaner" % "2.22",
  "jaxen" % "jaxen" % "1.1.6"
).map(_ % Test)

libraryDependencies ++= (appDeps ++ testDeps).map(_.excludeAll(
  ExclusionRule(organization = "commons-logging"),
  // No EhCache please we're British
  ExclusionRule(organization = "net.sf.ehcache"),
  ExclusionRule(organization = "org.ehcache"),
  ExclusionRule(organization = "ehcache"),
  // brought in by warwick utils, pulls in old XML shit
  ExclusionRule(organization = "rome"),
  ExclusionRule(organization = "dom4j"),
  // Tika pulls in slf4j-log4j12
  ExclusionRule(organization = "org.slf4j", name = "slf4j-log4j12")
))

libraryDependencies += specs2 % Test

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator

routesImport ++= Seq(
  "system.Binders._",
)

// Because jclouds is terrible
dependencyOverrides += "com.google.guava" % "guava" % "22.0"

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

def runWebpack(file: File): Int = Process("npm run build", file).!

lazy val npmAudit = taskKey[Unit]("Run npm audit and parse the results as JUnit XML")
npmAudit := NodePackageAudit.audit(baseDirectory.value)

webpack := {
  if (runWebpack(baseDirectory.value) != 0) throw new Exception("Something went wrong when running webpack.")
}
webpack := webpack.dependsOn(npmAudit).value

// Generate a new AES key for object store encryption
lazy val newEncryptionKey = taskKey[Unit]("Generate and print a new encryption key")
newEncryptionKey := println(EncryptionKey.generate())

runner := runner.dependsOn(webpack).value
dist := dist.dependsOn(webpack).value
stage := stage.dependsOn(webpack).value

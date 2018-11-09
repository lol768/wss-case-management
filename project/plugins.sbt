// Warwick parent plugin
resolvers += "nexus" at "https://mvn.elab.warwick.ac.uk/nexus/content/groups/public"
credentials += Credentials(Path.userHome / ".ivy2" / ".credentials")
addSbtPlugin("uk.ac.warwick" % "play-warwick" % "0.7")

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.6.17")

// .tgz generator
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.10")

// OWASP dependency scanner https://github.com/albuch/sbt-dependency-check
addSbtPlugin("net.vonbuchholtz" % "sbt-dependency-check" % "0.2.7")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

// Warwick parent plugin
resolvers += "nexus" at "https://mvn.elab.warwick.ac.uk/nexus/repository/public-anonymous/"
addSbtPlugin("uk.ac.warwick" % "play-warwick" % "0.9")

// The Play plugin
addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.7.0")

// .tgz generator
addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "1.3.18")

// OWASP dependency scanner https://github.com/albuch/sbt-dependency-check
addSbtPlugin("net.vonbuchholtz" % "sbt-dependency-check" % "0.2.10")

addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")

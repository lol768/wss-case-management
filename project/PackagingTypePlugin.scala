import sbt._

// https://github.com/sbt/sbt/issues/3618
object PackagingTypePlugin extends AutoPlugin {
  override val buildSettings: Seq[Setting[_]] = {
    sys.props += "packaging.type" -> "jar"
    Nil
  }
}

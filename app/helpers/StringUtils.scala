package helpers

import uk.ac.warwick.util.core.{ StringUtils => Utils }
import language.implicitConversions

/**
  * Scala-style String utilities. Adds the methods as implicit methods
  * on String.
  */
trait StringUtils {
  class SuperString(string: String) {
    def hasText: Boolean = Utils hasText string
    def isEmptyOrWhitespace: Boolean = !hasText
    def hasLength: Boolean = Utils hasLength string
    def safeTrim: String = Option(string).map { _.trim }.getOrElse("")
    def safeSubstring(proposedStart: Int): String = Utils safeSubstring(string, proposedStart)
    def safeSubstring(proposedStart: Int, proposedEnd: Int): String = Utils safeSubstring(string, proposedStart, proposedEnd)
    def orEmpty: String = Option(string).getOrElse("")
    def maybeText: Option[String] = Option(string).filter(Utils.hasText)
    def textOrEmpty: String = maybeText.getOrElse("")
    def safeLowercase: String = Option(string).map { _.toLowerCase }.getOrElse("")
    def safeLength: Int = Option(string).fold(0) { _.length }
    def safeContains(substring: String): Boolean = Option(string).exists(_.contains(substring))
    def safeStartsWith(substring: String): Boolean = Option(string).exists(_.startsWith(substring))
  }

  implicit def StringToSuperString(string: String): SuperString = new SuperString(string)

  implicit class Regex(sc: StringContext) {
    def r = new util.matching.Regex(sc.parts.mkString, sc.parts.tail.map(_ => "x"): _*)
  }
}

object StringUtils extends StringUtils

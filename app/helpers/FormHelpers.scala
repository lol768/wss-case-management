package helpers

import java.time.OffsetDateTime

import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.data.{Forms, Mapping}
import warwick.core.helpers.JavaTime

import scala.collection.GenTraversableOnce

object FormHelpers {

  def nonEmpty(errorMessage: String): Constraint[GenTraversableOnce[_]] = Constraint[GenTraversableOnce[_]]("constraint.required") { o =>
    if (o.isEmpty) Invalid(ValidationError(errorMessage)) else Valid
  }

  val Html5LocalDateTimePattern = "yyyy-MM-dd'T'HH:mm"
  val offsetDateTime: Mapping[OffsetDateTime] =
    Forms.localDateTime(Html5LocalDateTimePattern).transform[OffsetDateTime](_.atZone(JavaTime.timeZone).toOffsetDateTime, _.toLocalDateTime)

}

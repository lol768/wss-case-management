package helpers

import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}

import scala.collection.GenTraversableOnce

object FormHelpers {

  def nonEmpty(errorMessage: String): Constraint[GenTraversableOnce[_]] = Constraint[GenTraversableOnce[_]]("constraint.required") { o =>
    if (o.isEmpty) Invalid(ValidationError(errorMessage)) else Valid
  }

}

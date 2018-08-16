package helpers

object ServiceResults {
  trait ServiceError {
    def message: String
    def cause: Option[Throwable] = None
  }

  type ServiceResult[A] = Either[Seq[_ <: ServiceError], A]

  def sequence[A](in: Seq[ServiceResult[A]]): ServiceResult[Seq[A]] = in.partition(_.isLeft) match {
    case (Nil, results) => Right(results.collect { case Right(x) => x })
    case (errors, _) => Left(errors.collect { case Left(x) => x }.flatten)
  }
}

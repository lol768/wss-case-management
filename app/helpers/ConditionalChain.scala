package helpers

object ConditionalChain {

  implicit class When[T](val target: T) extends AnyVal {
    def when(condition: Boolean)(fn: (T) => T): T = if (condition) fn(target) else target
  }

}
package helpers.caching

import helpers.JavaTime
import scala.reflect.runtime.universe._

case class CacheElement[V](value: V, created: Long, softExpiry: Long, mediumExpiry: Long)(implicit val typeTag: TypeTag[V]) {
  def isStale: Boolean = JavaTime.instant.getEpochSecond > mediumExpiry
  def isSlightlyStale: Boolean = JavaTime.instant.getEpochSecond > softExpiry
}

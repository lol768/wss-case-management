package helpers.caching

import warwick.core.helpers.JavaTime

import scala.reflect.runtime.universe._

@SerialVersionUID(9123323060467832121L)
case class CacheElement[V](value: V, created: Long, softExpiry: Long, mediumExpiry: Long)(implicit val typeTag: TypeTag[V]) {
  def isStale: Boolean = JavaTime.instant.getEpochSecond > mediumExpiry
  def isSlightlyStale: Boolean = JavaTime.instant.getEpochSecond > softExpiry
}

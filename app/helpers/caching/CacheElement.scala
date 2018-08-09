package helpers.caching

import helpers.JavaTime

case class CacheElement[V](value: V, created: Long, softExpiry: Long, mediumExpiry: Long) {
  def isStale: Boolean = JavaTime.instant.getEpochSecond > mediumExpiry
  def isSlightlyStale: Boolean = JavaTime.instant.getEpochSecond > softExpiry
}

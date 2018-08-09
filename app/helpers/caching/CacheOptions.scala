package helpers.caching

case class CacheOptions(
  noCache: Boolean
)

object CacheOptions {
  val default: CacheOptions = CacheOptions(noCache = false)
}
package helpers.caching

import scala.concurrent.duration.{Duration, FiniteDuration}

/**
  * Holds cache TTLs.
  *
  * If the hard TTL has expired we re-fetch the value. This could be infinite in which case we would always serve cached data
  * If the medium TTL has expired we try to re-fetch the value but will return the cached value if this fails
  * If the soft TTL has expired we will return the stale value and update the cache in the background
  *
  */
case class Ttl(soft: FiniteDuration, medium: FiniteDuration, hard: Duration) {
  if(hard.isFinite() && hard.toSeconds == 0)
    throw new IllegalArgumentException("Don't use a zero hard TTL as the memcached library treats it as Infinite")
  if (soft > medium)
    throw new IllegalArgumentException("Soft TTL can't be larger than the medium TTL")
  if (medium > hard)
    throw new IllegalArgumentException("Medium TTL can't be larger than the hard TTL")
}

object Ttl {
  /**
    * If your value is an Option, uses one TTL for Some values and
    * another TTL for None. Useful for caching not-found results for a shorter time.
    */
  def optionStrategy[A](someTtl: Ttl, noneTtl: Ttl): Option[A] => Ttl = a =>
    if (a.isDefined) someTtl
    else noneTtl
}
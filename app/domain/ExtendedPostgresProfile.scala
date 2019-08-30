package domain

import com.github.tminglei.slickpg._
import slick.basic.Capability
import slick.jdbc.JdbcCapabilities
import warwick.slick.jdbctypes.pg.{ExtendedPgSearchSupport, FixedPgLocalDateTypeSupport}
import warwick.slick.jdbctypes.{CustomJdbcDateTypesSupport, CustomStringJdbcTypeSupport}

trait ExtendedPostgresProfile
  extends ExPostgresProfile
    with ExtendedPgSearchSupport
    with PgArraySupport
    with PgJsonSupport
    with CustomStringJdbcTypeSupport
    with CustomJdbcDateTypesSupport
    with FixedPgLocalDateTypeSupport {

  override val pgjson = "jsonb"

  // Add back `capabilities.insertOrUpdate` to enable native `upsert` support; for postgres 9.5+
  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + JdbcCapabilities.insertOrUpdate

  override val columnTypes = new JdbcTypes

  class JdbcTypes extends super.JdbcTypes
    with NullByteStrippingStringType
    with JdbcDateTypesUtc
    with FixedLocalDateType

  override val api: API = new API {}

  trait API
    extends super.API
      with SearchImplicits
      with ExtendedSearchAssistants
      with ArrayImplicits
      with JsonImplicits
}

object ExtendedPostgresProfile extends ExtendedPostgresProfile

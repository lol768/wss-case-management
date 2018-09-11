package domain

import com.github.tminglei.slickpg._
import slick.basic.Capability
import slick.driver.JdbcProfile

trait ExtendedPostgresProfile
  extends ExPostgresProfile
    with PgSearchSupport {

  // Add back `capabilities.insertOrUpdate` to enable native `upsert` support; for postgres 9.5+
  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + JdbcProfile.capabilities.insertOrUpdate

  override val api: API = new API {}

  trait API
    extends super.API
      with SearchImplicits
      with SearchAssistants
}

object ExtendedPostgresProfile extends ExtendedPostgresProfile

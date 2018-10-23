package domain

import com.github.tminglei.slickpg._
import slick.ast.Library.SqlFunction
import slick.ast.LiteralNode
import slick.basic.Capability
import slick.jdbc.{JdbcCapabilities, JdbcType, PostgresProfile}
import slick.lifted.OptionMapperDSL

trait ExtendedPgSearchSupport extends PgSearchSupport { driver: PostgresProfile =>
  import driver.api._

  trait ExtendedSearchAssistants extends SearchAssistants {
    def prefixTsQuery[R](query: Rep[String], config: String = "english")(
      implicit tm1: JdbcType[TsVector], tm2: JdbcType[TsQuery], om: OptionMapperDSL.arg[String, String]#to[TsQuery, R]
    ): Rep[R] =
      om.column(
        SearchLibrary.ToTsQuery,
        LiteralNode(config),
        om.column(SearchLibrary.Or,
          om.column(
            new SqlFunction("quote_literal"),
            query.toNode
          ).toNode,
          ":*".bind.toNode
        ).toNode
      )
  }
}

trait ExtendedPostgresProfile
  extends ExPostgresProfile
    with ExtendedPgSearchSupport {

  // Add back `capabilities.insertOrUpdate` to enable native `upsert` support; for postgres 9.5+
  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + JdbcCapabilities.insertOrUpdate

  override val api: API = new API {}

  trait API
    extends super.API
      with SearchImplicits
      with ExtendedSearchAssistants
}

object ExtendedPostgresProfile extends ExtendedPostgresProfile

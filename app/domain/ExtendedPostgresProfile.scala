package domain

import java.sql.{PreparedStatement, ResultSet}

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
        // regexp_replace(regexp_replace(trim(?), '[^A-Za-z0-9_\s-]', ''), '\s+', ':* & ') || ':*'
        om.column(SearchLibrary.Or,
          // regexp_replace(regexp_replace(trim(?), '[^A-Za-z0-9_\s-]', ''), '\s+', ':* & ')
          om.column(
            new SqlFunction("regexp_replace"),
            // regexp_replace(trim(?), '[^A-Za-z0-9_\s-]', '')
            om.column(
              new SqlFunction("regexp_replace"),
              om.column(
                new SqlFunction("trim"),
                query.toNode
              ).toNode,
              "[^A-Za-z0-9_\\s-]".toNode,
              "".toNode,
              "g".toNode
            ).toNode,
            "\\s+".toNode,
            ":* & ".toNode,
            "g".toNode
          ).toNode,
          ":*".bind.toNode
        ).toNode
      )
  }
}

trait ExtendedPostgresProfile
  extends ExPostgresProfile
    with ExtendedPgSearchSupport
    with PgArraySupport {

  // Add back `capabilities.insertOrUpdate` to enable native `upsert` support; for postgres 9.5+
  override protected def computeCapabilities: Set[Capability] =
    super.computeCapabilities + JdbcCapabilities.insertOrUpdate

  override val columnTypes = new JdbcTypes

  class JdbcTypes extends super.JdbcTypes {
    override val stringJdbcType: StringJdbcType = new StringJdbcType

    // CASE-373 Strip out null bytes before they get to Postgres
    class StringJdbcType extends super.StringJdbcType {
      private def stripNullBytes(v: String): String = v.replace("\u0000", "")

      override def setValue(v: String, p: PreparedStatement, idx: Int): Unit = super.setValue(stripNullBytes(v), p, idx)
      override def updateValue(v: String, r: ResultSet, idx: Int): Unit = super.updateValue(stripNullBytes(v), r, idx)
      override def valueToSQLLiteral(value: String): String = super.valueToSQLLiteral(stripNullBytes(value))
    }
  }

  override val api: API = new API {}

  trait API
    extends super.API
      with SearchImplicits
      with ExtendedSearchAssistants
      with ArrayImplicits
}

object ExtendedPostgresProfile extends ExtendedPostgresProfile

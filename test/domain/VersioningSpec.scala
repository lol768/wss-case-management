package domain

import java.time.OffsetDateTime

import akka.Done
import domain.CustomJdbcTypes._
import domain.ExtendedPostgresProfile.api._
import domain.VersioningSpec._
import domain.dao.AbstractDaoTest
import org.scalatest.BeforeAndAfterEach
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import services.AuditLogContext
import slick.jdbc.JdbcProfile
import warwick.core.helpers.JavaTime
import warwick.sso.{GroupName, Usercode}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.Try

object VersioningSpec {
  case class Account(
    usercode: Usercode,
    webgroup: GroupName,
    version: OffsetDateTime = JavaTime.offsetDateTime
  ) extends Versioned[Account] {
    override def atVersion(at: OffsetDateTime): Account = copy(version = at)
    override def storedVersion[B <: StoredVersion[Account]](operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): B =
      AccountVersion.versioned(this, operation, timestamp).asInstanceOf[B]
  }

  case class AccountVersion(
    usercode: Usercode,
    webgroup: GroupName,
    version: OffsetDateTime,
    operation: DatabaseOperation,
    timestamp: OffsetDateTime,
    auditUser: Option[Usercode]
  ) extends StoredVersion[Account]

  object AccountVersion {
    def tupled = (apply _).tupled

    def versioned(account: Account, operation: DatabaseOperation, timestamp: OffsetDateTime)(implicit ac: AuditLogContext): AccountVersion =
      AccountVersion(
        account.usercode,
        account.webgroup,
        account.version,
        operation,
        timestamp,
        ac.usercode
      )
  }

  object Account extends Versioning {
    def tupled = (apply _).tupled

    sealed trait AccountProperties {
      self: Table[_] =>

      def webgroup = column[GroupName]("WEBGROUP")
      def version = column[OffsetDateTime]("VERSION_UTC")
    }

    class Accounts(tag: Tag) extends Table[Account](tag, "ACCOUNT") with VersionedTable[Account] with AccountProperties {
      override def matchesPrimaryKey(other: Account): Rep[Boolean] = usercode === other.usercode

      def usercode = column[Usercode]("USERCODE", O.PrimaryKey)

      def * = (usercode, webgroup, version).mapTo[Account]
    }

    class AccountVersions(tag: Tag) extends Table[AccountVersion](tag, "ACCOUNT_VERSION") with StoredVersionTable[Account] with AccountProperties {
      def usercode = column[Usercode]("USERCODE")
      def operation = column[DatabaseOperation]("VERSION_OPERATION")
      def timestamp = column[OffsetDateTime]("VERSION_TIMESTAMP_UTC")
      def auditUser = column[Option[Usercode]]("VERSION_USER")

      def * = (usercode, webgroup, version, operation, timestamp, auditUser).mapTo[AccountVersion]
      def pk = primaryKey("pk_accountversions", (usercode, timestamp))
      def idx = index("idx_accountversions", (usercode, version))
    }

    val accounts: VersionedTableQuery[Account, AccountVersion, Accounts, AccountVersions] =
      VersionedTableQuery(TableQuery[Accounts], TableQuery[AccountVersions])

    implicit class AccountExtensions[C[_]](q: Query[Accounts, Account, C]) {
      def withPreviousVersions = q.joinLeft(accounts.versionsTable).on { case (a, v) => a.usercode === v.usercode && v.version < a.version }
    }
  }

  class SlickAccountDao (
    protected val dbConfigProvider: DatabaseConfigProvider
  )(implicit ec: ExecutionContext) extends HasDatabaseConfigProvider[ExtendedPostgresProfile] {
    import Account._

    // TODO refactor as action/query generator

    def list(): Future[Seq[Account]] = {
      db.run(accounts.result.transactionally)
    }

    def insert(account: Account)(implicit ac: AuditLogContext): Future[Account] =
      db.run((accounts += account).transactionally)

    def update(account: Account)(implicit ac: AuditLogContext): Future[Account] = {
      db.run(accounts.update(account).transactionally)
    }

    def delete(account: Account)(implicit ac: AuditLogContext): Future[Done] = {
      db.run(accounts.delete(account).transactionally)
    }
  }
}

class VersioningSpec extends AbstractDaoTest with BeforeAndAfterEach {

  val accountDao = new SlickAccountDao(dbConfigProvider)

  import Account._

  trait EmptyDatabaseFixture {
    execWithCommit(
      (accounts.table.schema ++ accounts.versionsTable.schema).create
    )
  }

  override protected def afterEach(): Unit = {
    execWithCommit(
      (accounts.table.schema ++ accounts.versionsTable.schema).drop
    )
  }

  "SlickAccountDao" should {
    "insert a row into the versions table on insert" in new EmptyDatabaseFixture {
      val account = Account(Usercode("cuscav"), GroupName("in-webdev"))

      private val insertedAccount = accountDao.insert(account).futureValue
      insertedAccount.usercode mustBe account.usercode
      insertedAccount.webgroup mustBe account.webgroup

      accountDao.list().futureValue.size mustBe 1
      exec(accounts.versionsTable.result).length mustBe 1

      // If I try and insert it again, it should throw an error but not insert an extra row into versions
      Try(accountDao.insert(account).futureValue).isFailure mustBe true
      exec(accounts.versionsTable.result).length mustBe 1
    }

    "insert a row into the versions table on update" in new EmptyDatabaseFixture {
      private val account = accountDao.insert(Account(Usercode("cuscav"), GroupName("in-webdev"))).futureValue

      // Just the I
      exec(accounts.versionsTable.result).length mustBe 1

      private val updatedAccount = accountDao.update(account.copy(webgroup = GroupName("in-elab"))).futureValue
      updatedAccount.usercode mustBe account.usercode
      updatedAccount.webgroup mustBe GroupName("in-elab")

      accountDao.list().futureValue.size mustBe 1
      exec(accounts.versionsTable.result).length mustBe 2 // I, U

      // Go back to the original group name
      private val updatedAccount2 = accountDao.update(updatedAccount.copy(webgroup = GroupName("in-webdev"))).futureValue
      updatedAccount2.usercode mustBe account.usercode
      updatedAccount2.webgroup mustBe GroupName("in-webdev")

      accountDao.list().futureValue.size mustBe 1
      exec(accounts.versionsTable.result).length mustBe 3 // I, U, U
    }

    "fail optimistic locking if trying to update a row with the wrong version" in new EmptyDatabaseFixture {
      private val account = accountDao.insert(Account(Usercode("cuscav"), GroupName("in-webdev"))).futureValue
      accountDao.update(account.copy(webgroup = GroupName("in-elab"))).futureValue

      exec(accounts.versionsTable.result).length mustBe 2 // I, U

      // Try and use the original account again for the update, version mismatch, OLE
      Try(accountDao.update(account.copy(webgroup = GroupName("in-all"))).futureValue).isFailure mustBe true

      exec(accounts.versionsTable.result).length mustBe 2 // Still I, U
    }

    "insert a row into the versions table on delete" in new EmptyDatabaseFixture {
      private val account = accountDao.insert(Account(Usercode("cuscav"), GroupName("in-webdev"))).futureValue

      // Just the I
      exec(accounts.versionsTable.result).length mustBe 1
      accountDao.list().futureValue.size mustBe 1

      accountDao.delete(account).futureValue mustBe Done

      // I, D
      exec(accounts.versionsTable.result).length mustBe 2
      accountDao.list().futureValue.size mustBe 0
    }

    "fail optimistic locking if trying to delete a row with the wrong version" in new EmptyDatabaseFixture {
      private val account = accountDao.insert(Account(Usercode("cuscav"), GroupName("in-webdev"))).futureValue
      accountDao.update(account.copy(webgroup = GroupName("in-elab"))).futureValue

      exec(accounts.versionsTable.result).length mustBe 2 // I, U

      // Try and delete the original account again for the update, version mismatch, OLE
      Try(accountDao.delete(account).futureValue).isFailure mustBe true

      exec(accounts.versionsTable.result).length mustBe 2 // Still I, U
    }
  }

}

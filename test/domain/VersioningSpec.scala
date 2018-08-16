package domain

import java.time.ZonedDateTime

import akka.Done
import domain.CustomJdbcTypes._
import domain.VersioningSpec._
import domain.dao.AbstractDaoTest
import helpers.JavaTime
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import warwick.sso.{GroupName, Usercode}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.Try

object VersioningSpec {
  case class Account(
    usercode: Usercode,
    webgroup: GroupName,
    version: ZonedDateTime = JavaTime.zonedDateTime
  ) extends Versioned[Account] {
    override def atVersion(at: ZonedDateTime): Account = copy(version = at)
    override def storedVersion[B <: StoredVersion[Account]](operation: DatabaseOperation, timestamp: ZonedDateTime): B =
      AccountVersion.versioned(this, operation, timestamp).asInstanceOf[B]
  }

  case class AccountVersion(
    usercode: Usercode,
    webgroup: GroupName,
    version: ZonedDateTime,
    operation: DatabaseOperation,
    timestamp: ZonedDateTime
  ) extends StoredVersion[Account]

  object AccountVersion {
    def tupled = (apply _).tupled

    def versioned(account: Account, operation: DatabaseOperation, timestamp: ZonedDateTime): AccountVersion =
      AccountVersion(
        account.usercode,
        account.webgroup,
        account.version,
        operation,
        timestamp
      )
  }

  object Account extends Versioning {
    def tupled = (apply _).tupled

    sealed trait AccountProperties {
      self: Table[_] =>

      def webgroup = column[GroupName]("WEBGROUP")
      def version = column[ZonedDateTime]("VERSION")
    }

    class Accounts(tag: Tag) extends Table[Account](tag, "ACCOUNT") with VersionedTable[Account] with AccountProperties {
      override def matchesPrimaryKey(other: Account): Rep[Boolean] = usercode === other.usercode

      def usercode = column[Usercode]("USERCODE", O.PrimaryKey)

      def * = (usercode, webgroup, version).mapTo[Account]
    }

    class AccountVersions(tag: Tag) extends Table[AccountVersion](tag, "ACCOUNT_VERSION") with StoredVersionTable[Account] with AccountProperties {
      def usercode = column[Usercode]("USERCODE")
      def operation = column[DatabaseOperation]("VERSION_OPERATION")
      def timestamp = column[ZonedDateTime]("VERSION_TIMESTAMP")

      def * = (usercode, webgroup, version, operation, timestamp).mapTo[AccountVersion]
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
  )(implicit ec: ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {
    import Account._

    // TODO refactor as action/query generator

    def list(): Future[Seq[Account]] = {
      db.run(accounts.result.transactionally)
    }

    def insert(account: Account): Future[Account] =
      db.run((accounts += account).transactionally)

    def update(account: Account): Future[Account] = {
      db.run(accounts.update(account).transactionally)
    }

    def delete(account: Account): Future[Done] = {
      db.run(accounts.delete(account).transactionally)
    }
  }
}

class VersioningSpec extends AbstractDaoTest {

  val accountDao = new SlickAccountDao(dbConfigProvider)

  import Account._

  trait EmptyDatabaseFixture {
    def db: Database = dbConfig.db

    db.run((
      sqlu"""DROP TABLE IF EXISTS ACCOUNT""" andThen
      sqlu"""DROP TABLE IF EXISTS ACCOUNT_VERSION""" andThen
      (accounts.table.schema ++ accounts.versionsTable.schema).create
    ).transactionally).futureValue
  }

  "SlickAccountDao" should {
    "insert a row into the versions table on insert" in new EmptyDatabaseFixture {
      val account = Account(Usercode("cuscav"), GroupName("in-webdev"))

      val insertedAccount = accountDao.insert(account).futureValue
      insertedAccount.usercode mustBe account.usercode
      insertedAccount.webgroup mustBe account.webgroup

      accountDao.list().futureValue.size mustBe 1
      db.run(accounts.versionsTable.result).futureValue.length mustBe 1

      // If I try and insert it again, it should throw an error but not insert an extra row into versions
      Try(accountDao.insert(account).futureValue).isFailure mustBe true
      db.run(accounts.versionsTable.result).futureValue.length mustBe 1
    }

    "insert a row into the versions table on update" in new EmptyDatabaseFixture {
      val account = accountDao.insert(Account(Usercode("cuscav"), GroupName("in-webdev"))).futureValue

      // Just the I
      db.run(accounts.versionsTable.result).futureValue.length mustBe 1

      val updatedAccount = accountDao.update(account.copy(webgroup = GroupName("in-elab"))).futureValue
      updatedAccount.usercode mustBe account.usercode
      updatedAccount.webgroup mustBe GroupName("in-elab")

      accountDao.list().futureValue.size mustBe 1
      db.run(accounts.versionsTable.result).futureValue.length mustBe 2 // I, U

      // Go back to the original group name
      val updatedAccount2 = accountDao.update(updatedAccount.copy(webgroup = GroupName("in-webdev"))).futureValue
      updatedAccount2.usercode mustBe account.usercode
      updatedAccount2.webgroup mustBe GroupName("in-webdev")

      accountDao.list().futureValue.size mustBe 1
      db.run(accounts.versionsTable.result).futureValue.length mustBe 3 // I, U, U
    }

    "fail optimistic locking if trying to update a row with the wrong version" in new EmptyDatabaseFixture {
      val account = accountDao.insert(Account(Usercode("cuscav"), GroupName("in-webdev"))).futureValue
      accountDao.update(account.copy(webgroup = GroupName("in-elab"))).futureValue

      db.run(accounts.versionsTable.result).futureValue.length mustBe 2 // I, U

      // Try and use the original account again for the update, version mismatch, OLE
      Try(accountDao.update(account.copy(webgroup = GroupName("in-all"))).futureValue).isFailure mustBe true

      db.run(accounts.versionsTable.result).futureValue.length mustBe 2 // Still I, U
    }

    "insert a row into the versions table on delete" in new EmptyDatabaseFixture {
      val account = accountDao.insert(Account(Usercode("cuscav"), GroupName("in-webdev"))).futureValue

      // Just the I
      db.run(accounts.versionsTable.result).futureValue.length mustBe 1
      accountDao.list().futureValue.size mustBe 1

      accountDao.delete(account).futureValue mustBe Done

      // I, D
      db.run(accounts.versionsTable.result).futureValue.length mustBe 2
      accountDao.list().futureValue.size mustBe 0
    }

    "fail optimistic locking if trying to delete a row with the wrong version" in new EmptyDatabaseFixture {
      val account = accountDao.insert(Account(Usercode("cuscav"), GroupName("in-webdev"))).futureValue
      accountDao.update(account.copy(webgroup = GroupName("in-elab"))).futureValue

      db.run(accounts.versionsTable.result).futureValue.length mustBe 2 // I, U

      // Try and delete the original account again for the update, version mismatch, OLE
      Try(accountDao.delete(account).futureValue).isFailure mustBe true

      db.run(accounts.versionsTable.result).futureValue.length mustBe 2 // Still I, U
    }
  }

}

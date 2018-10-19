package services

import com.google.inject.ImplementedBy
import domain.ExtendedPostgresProfile.api._
import domain.dao.MemberDao.StoredMember
import domain.dao.{DaoRunner, MemberDao}
import domain.{Member, Teams}
import helpers.ServiceResults.ServiceResult
import javax.inject.Inject
import slick.dbio.DBIOAction
import warwick.sso.{GroupService, UserLookupService, Usercode}
import helpers.ServiceResults.Implicits._
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object MemberService {
  val UpdateRequiredWindow: FiniteDuration = 7.days
}

@ImplementedBy(classOf[MemberServiceImpl])
trait MemberService {
  def getOrAddMember(usercode: Usercode)(implicit ac: AuditLogContext): Future[ServiceResult[Member]]
  def getOrAddMember(usercode: Option[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[Option[Member]]]
  def getOrAddMembers(usercodes: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[Seq[Member]]]
  def getForUpdate(implicit ac: AuditLogContext): Future[ServiceResult[Seq[Member]]]
  def updateMembers(details: Map[Usercode, Option[String]])(implicit ac: AuditLogContext): Future[ServiceResult[Seq[Member]]]
  def search(query: String)(implicit ac: AuditLogContext): Future[ServiceResult[Seq[Member]]]
}

class MemberServiceImpl @Inject()(
  userLookupService: UserLookupService,
  permissionService: PermissionService,
  groupService: GroupService,
  daoRunner: DaoRunner,
  dao: MemberDao,
)(implicit ec: ExecutionContext) extends MemberService {

  override def getOrAddMember(usercode: Usercode)(implicit ac: AuditLogContext): Future[ServiceResult[Member]] =
    getOrAddMembers(Set(usercode)).map(_.map(_.head))

  override def getOrAddMember(usercode: Option[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[Option[Member]]] =
    usercode match {
      case Some(u) => getOrAddMember(u).map(_.map(Option.apply))
      case _ => Future.successful(Right(None))
    }

  override def getOrAddMembers(usercodes: Set[Usercode])(implicit ac: AuditLogContext): Future[ServiceResult[Seq[Member]]] = {
    daoRunner.run(DBIOAction.sequence(usercodes.toSeq.map(dao.get))).flatMap(_.partition(_.isEmpty) match {
      case (Nil, existing) =>
        Future.successful(Right(existing.flatMap(_.map(_.asMember))))
      case (_, existing) =>
        val missing = usercodes.diff(existing.flatMap(_.map(_.usercode)).toSet)
        val userMap = userLookupService.getUsers(missing.toSeq).toOption.getOrElse(Map())
          val (inSSO, stillMissing) = missing.partition(u => userMap.get(u).flatMap(_.name.full).nonEmpty)
          val ssoInserts = inSSO.toSeq.map(usercode => dao.insert(StoredMember(usercode, userMap(usercode).name.full)))
          val missingInserts = stillMissing.toSeq.map(id => dao.insert(StoredMember(id, None)))
          daoRunner.run(DBIOAction.sequence(ssoInserts ++ missingInserts))
            .map(_.map(_.asMember))
            .map { added =>
              Right(added ++ existing.flatMap(_.map(_.asMember)))
            }
    })

  }

  override def getForUpdate(implicit ac: AuditLogContext): Future[ServiceResult[Seq[Member]]] =
    daoRunner.run(dao.getOlderThan(MemberService.UpdateRequiredWindow).result)
      .map(r => Right(r.map(_.asMember)))

  override def updateMembers(details: Map[Usercode, Option[String]])(implicit ac: AuditLogContext): Future[ServiceResult[Seq[Member]]] =
    daoRunner.run(for {
      existing <- dao.get(details.keySet)
      updated <- DBIOAction.sequence(
        existing.map(member => dao.update(member.copy(fullName = details(member.usercode)), member.version))
      )
    } yield Right(updated.map(_.asMember)))

  override def search(query: String)(implicit ac: AuditLogContext): Future[ServiceResult[Seq[Member]]] = {
    val usercodeResult = userLookupService.getUser(Usercode(query)).toOption
    usercodeResult.map(user => getOrAddMember(user.usercode).map(_.map(m => Seq(m)))).getOrElse {
      // Populate Member table
      getOrAddAllMembers.successFlatMapTo(_ =>
        // Then search them
        daoRunner.run(dao.findByNameQuery(query).result).map(r => Right(r.map(_.asMember)))
      )
    }
  }

  private def getOrAddAllMembers(implicit ac: AuditLogContext): Future[ServiceResult[Seq[Member]]] = {
    val allTeamMembers = Teams.all.map(permissionService.webgroupFor)
      .flatMap(groupService.getWebGroup(_).toOption).flatten
      .flatMap(g => g.owners ++ g.members).toSet

    getOrAddMembers(allTeamMembers)
  }

}

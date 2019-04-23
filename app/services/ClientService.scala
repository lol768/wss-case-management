package services

import com.google.inject.ImplementedBy
import domain.Client
import domain.dao.ClientDao.StoredClient
import domain.dao.{ClientDao, DaoRunner}
import warwick.core.helpers.ServiceResults.ServiceResult
import javax.inject.Inject
import services.tabula.ProfileService
import slick.dbio.DBIOAction
import warwick.sso.UniversityID
import warwick.core.helpers.ServiceResults.Implicits._

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import domain.ExtendedPostgresProfile.api._
import warwick.core.helpers.ServiceResults
import warwick.core.timing.TimingContext

object ClientService {
  val UpdateRequiredWindow: FiniteDuration = 7.days
}

@ImplementedBy(classOf[ClientServiceImpl])
trait ClientService {
  def find(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Option[Client]]]
  def getOrAddClients(universityIDs: Set[UniversityID])(implicit ac: AuditLogContext): Future[ServiceResult[Seq[Client]]]
  def getForUpdate(implicit ac: AuditLogContext): Future[ServiceResult[Seq[Client]]]
  def updateClients(details: Map[UniversityID, Option[String]])(implicit ac: AuditLogContext): Future[ServiceResult[Seq[Client]]]
  def search(query: String)(implicit t: TimingContext): Future[ServiceResult[Seq[Client]]]
}

class ClientServiceImpl @Inject()(
  profileService: ProfileService,
  daoRunner: DaoRunner,
  dao: ClientDao,
)(implicit ec: ExecutionContext) extends ClientService {

  override def find(universityID: UniversityID)(implicit t: TimingContext): Future[ServiceResult[Option[Client]]] =
    daoRunner.run(dao.get(universityID)).map { c => ServiceResults.success(c.map(_.asClient)) }

  override def getOrAddClients(universityIDs: Set[UniversityID])(implicit ac: AuditLogContext): Future[ServiceResult[Seq[Client]]] = {
    daoRunner.run(DBIOAction.sequence(universityIDs.toSeq.map(dao.get))).flatMap(_.partition(_.isEmpty) match {
      case (Nil, existing) =>
        Future.successful(Right(existing.flatMap(_.map(_.asClient))))
      case (_, existing) =>
        val missing = universityIDs.diff(existing.flatMap(_.map(_.universityID)).toSet)
        profileService.getProfiles(missing).successFlatMapTo { profileMap =>
          val (inSITS, stillMissing) = missing.partition(profileMap.keySet.contains)
          val sitsInserts = inSITS.toSeq.map(id => dao.insert(StoredClient(id, Some(profileMap(id).fullName))))
          val missingInserts = stillMissing.toSeq.map(id => dao.insert(StoredClient(id, None)))
          daoRunner.run(DBIOAction.sequence(sitsInserts ++ missingInserts))
            .map(_.map(_.asClient))
            .map { added =>
              Right(added ++ existing.flatMap(_.map(_.asClient)))
            }
        }
    })

  }

  override def getForUpdate(implicit ac: AuditLogContext): Future[ServiceResult[Seq[Client]]] =
    daoRunner.run(dao.getOlderThan(ClientService.UpdateRequiredWindow).result)
      .map(r => Right(r.map(_.asClient)))

  override def updateClients(details: Map[UniversityID, Option[String]])(implicit ac: AuditLogContext): Future[ServiceResult[Seq[Client]]] =
    daoRunner.run(for {
      existing <- dao.get(details.keySet)
      updated <- DBIOAction.sequence(
        existing.map(client => dao.update(client.copy(fullName = details(client.universityID)), client.version))
      )
    } yield Right(updated.map(_.asClient)))

  override def search(query: String)(implicit t: TimingContext): Future[ServiceResult[Seq[Client]]] =
    if (query.matches("^\\d{7,}$")) {
      daoRunner.run(dao.get(UniversityID(query))).map(r => Right(r.toSeq.map(_.asClient)))
    } else {
      daoRunner.run(dao.findByNameQuery(query).result).map(r => Right(r.map(_.asClient)))
    }

}

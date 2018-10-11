package services

import com.google.inject.ImplementedBy
import domain.Client
import domain.dao.ClientDao.StoredClient
import domain.dao.{ClientDao, DaoRunner}
import helpers.ServiceResults.ServiceResult
import javax.inject.Inject
import services.tabula.ProfileService
import slick.dbio.DBIOAction
import warwick.sso.UniversityID
import helpers.ServiceResults.Implicits._
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[ClientServiceImpl])
trait ClientService {
  def getOrAddClients(universityIDs: Set[UniversityID])(implicit ac: AuditLogContext): Future[ServiceResult[Seq[Client]]]
  def updateClients(universityIDs: Set[UniversityID])(implicit ac: AuditLogContext): Future[ServiceResult[Seq[Client]]]
}

class ClientServiceImpl @Inject()(
  profileService: ProfileService,
  daoRunner: DaoRunner,
  dao: ClientDao,
)(implicit ec: ExecutionContext) extends ClientService {

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

  override def updateClients(universityIDs: Set[UniversityID])(implicit ac: AuditLogContext): Future[ServiceResult[Seq[Client]]] = ???
}

package services

import com.google.inject.ImplementedBy
import domain.UserPreferences
import domain.dao.UserPreferencesDao.StoredUserPreferences
import domain.dao.{DaoRunner, UserPreferencesDao}
import helpers.ServiceResults._
import javax.inject.{Inject, Singleton}
import play.api.libs.json.Json
import warwick.core.Logging
import warwick.core.helpers.JavaTime
import warwick.core.timing.TimingContext
import warwick.sso.Usercode

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[UserPreferencesServiceImpl])
trait UserPreferencesService {
  def get(usercode: Usercode)(implicit t: TimingContext): Future[ServiceResult[UserPreferences]]
  def get(usercodes: Set[Usercode])(implicit t: TimingContext): Future[ServiceResult[Map[Usercode, UserPreferences]]]
  def update(usercode: Usercode, preferences: UserPreferences)(implicit ac: AuditLogContext): Future[ServiceResult[UserPreferences]]
}

@Singleton
class UserPreferencesServiceImpl @Inject()(
  auditService: AuditService,
  dao: UserPreferencesDao,
  daoRunner: DaoRunner,
)(implicit executionContext: ExecutionContext) extends UserPreferencesService with Logging {

  override def get(usercode: Usercode)(implicit t: TimingContext): Future[ServiceResult[UserPreferences]] =
    daoRunner.run(dao.find(usercode))
      .map { p => success(p.fold(UserPreferences.default)(_.parsed)) }

  override def get(usercodes: Set[Usercode])(implicit t: TimingContext): Future[ServiceResult[Map[Usercode, UserPreferences]]] =
    daoRunner.run(dao.find(usercodes))
      .map { result => success(
        usercodes.map(usercode => usercode ->
          result.find(_.usercode == usercode)
            .fold(UserPreferences.default)(_.parsed)
        ).toMap
      )}

  override def update(usercode: Usercode, preferences: UserPreferences)(implicit ac: AuditLogContext): Future[ServiceResult[UserPreferences]] =
    daoRunner.run(
      dao.upsert(
        StoredUserPreferences(
          usercode,
          Json.toJson(preferences)(UserPreferences.formatter),
          JavaTime.offsetDateTime,
        )
      )
    ).map { p => success(p.parsed) }

}

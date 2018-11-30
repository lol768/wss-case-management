package domain

import java.time.OffsetDateTime

import domain.dao.CaseDao.{StoredCaseClient, StoredCaseClientVersion, StoredCaseTag, StoredCaseTagVersion}
import domain.dao.DSADao.{StoredDSAFundingType, StoredDSAFundingTypeVersion}
import play.api.libs.json.{JsValue, Json, Writes}
import warwick.sso.{User, Usercode}

object History {

  type FieldHistory[A] = Seq[(A, OffsetDateTime, Option[Either[Usercode, User]])]

  def optionalHistory[A](emptyMessage: String, history: FieldHistory[Option[A]], toHistoryDescription: A => String): Seq[(String, OffsetDateTime, Option[Either[Usercode, User]])] =
    history.map{ case (value, v, u) => (value.map(toHistoryDescription).getOrElse(emptyMessage), v, u) }

  def toUsercodeOrUser(u: Usercode)(implicit usersByUsercode: Map[Usercode, User]): Either[Usercode, User] = usersByUsercode.get(u).map(Right.apply).getOrElse(Left(u))

  def simpleFieldHistory[A <: Versioned[A], B <: StoredVersion[A], C](history: Seq[B], getValue: B => C)(implicit usersByUsercode: Map[Usercode, User]): FieldHistory[C] =
    flatten(history.map(c => (getValue(c), c.version, c.auditUser))).map {
      case (c,v,u) => (c, v, u.map(toUsercodeOrUser))
    }

  def flatten[A](items: Seq[(A, OffsetDateTime, Option[Usercode])]): Seq[(A, OffsetDateTime, Option[Usercode])] = (items.toList match {
    case Nil => Nil
    case head :: Nil => Seq(head)
    case head :: tail => tail.foldLeft(Seq(head)) { (foldedItems, item) =>
      if (foldedItems.last._1 != item._1) {
        foldedItems :+ item
      } else {
        foldedItems
      }
    }
  }).reverse

  def flattenCollection[A <: Versioned[A], B <: StoredVersion[A]](items: Seq[B]): Seq[(Set[A], OffsetDateTime, Option[Usercode])] = {
    def toSpecificItem(item: B): A = item match {
      case tag: StoredCaseTagVersion => StoredCaseTag(tag.caseId, tag.caseTag, tag.version).asInstanceOf[A]
      case owner: OwnerVersion => Owner(owner.entityId, owner.entityType, owner.userId, owner.outlookId, owner.version).asInstanceOf[A]
      case client: StoredCaseClientVersion => StoredCaseClient(client.caseId, client.universityID, client.version).asInstanceOf[A]
      case ft: StoredDSAFundingTypeVersion => StoredDSAFundingType(ft.dsaApplicationID, ft.fundingType, ft.version).asInstanceOf[A]
      case _ => throw new IllegalArgumentException("Unsupported versioned item")
    }
    val result = items.toList.sortBy(_.timestamp) match {
      case Nil => Nil
      case head :: Nil => List((Set(toSpecificItem(head)), head.version, head.auditUser))
      case head :: tail => tail.foldLeft[Seq[(Set[A], OffsetDateTime, Option[Usercode])]](Seq((Set(toSpecificItem(head)), head.version, head.auditUser))) { (result, item) =>
        if (item.operation == DatabaseOperation.Insert) {
          result.:+((result.last._1 + toSpecificItem(item), item.timestamp, item.auditUser))
        } else {
          result.:+((result.last._1 - toSpecificItem(item), item.timestamp, item.auditUser))
        }
      }
    }
    result
      // Group by identical timestamp and take the last one so bulk operations show as a single action
      .groupBy { case (_, t, _) => t }.mapValues(_.last).values.toSeq
      .sortBy { case (_, t, _) => t }
      .reverse
  }

  def toJson[A](items: FieldHistory[A])(implicit itemWriter: Writes[A]): JsValue =
    Json.toJson(items.map { case (item, version, auditUser) => Json.obj(
      "value" -> Json.toJson(item),
      "version" -> version,
      "user" -> auditUser.map(_.fold(
        usercode => usercode.string,
        user => user.name.full.getOrElse(user.usercode.string)
      ))
    )})

}

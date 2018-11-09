import akka.Done
import domain.ExtendedPostgresProfile.api._

import scala.concurrent.ExecutionContext

package object services {

  case class UpdateDifferencesResult[A](
    added: Seq[A],
    removed: Seq[A],
    unchanged: Seq[A]
  ) {
    val all: Seq[A] = unchanged ++ added
  }

  def updateDifferencesDBIO[A, B](items: Set[B], query: Query[Table[A], A, Seq], map: A => B, comap: B => A, insert: Set[A] => DBIO[Seq[A]], delete: Set[A] => DBIO[Done])(implicit ec: ExecutionContext): DBIO[UpdateDifferencesResult[A]] = {
    val existing = query.result
    val unchanged = existing.map(_.filter(e => items.contains(map(e))))

    val needsRemoving = existing.map(_.filterNot(e => items.contains(map(e))))
    val removals = needsRemoving.flatMap(r => delete(r.toSet).map(_ => r))

    val needsAdding = existing.map(e => items.toSeq.filterNot(e.map(map).contains))
    val additions = needsAdding.flatMap(a => insert(a.map(comap).toSet))

    for {
      u <- unchanged
      removed <- removals
      added <- additions
    } yield UpdateDifferencesResult(added, removed, u)
  }

}

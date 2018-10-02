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

  def updateDifferencesDBIO[A, B](items: Set[B], query: Query[Table[A], A, Seq], map: A => B, comap: B => A, insert: A => DBIO[A], delete: A => DBIO[Done])(implicit ec: ExecutionContext): DBIO[UpdateDifferencesResult[A]] = {
    val existing = query.result
    val unchanged = existing.map(_.filter(e => items.contains(map(e))))

    val needsRemoving = existing.map(_.filterNot(e => items.contains(map(e))))
    val removals = needsRemoving.flatMap(r => DBIO.sequence(r.map(i => delete(i).map(_ => i))))

    val needsAdding = existing.map(e => items.toSeq.filterNot(e.map(map).contains))
    val additions = needsAdding.flatMap(a => DBIO.sequence(a.map(comap).map(insert)))

    for {
      removed <- removals
      added <- additions
      u <- unchanged
    } yield UpdateDifferencesResult(added, removed, u)
  }

}

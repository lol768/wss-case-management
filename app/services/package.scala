import akka.Done
import domain.ExtendedPostgresProfile.api._

import scala.concurrent.ExecutionContext

package object services {

  def updateDifferencesDBIO[A, B](items: Set[B], query: Query[Table[A], A, Seq], map: A => B, comap: B => A, insert: A => DBIO[A], delete: A => DBIO[Done])(implicit ec: ExecutionContext): DBIO[(Seq[A], Seq[A])] = {
    val existing = query.result

    val needsRemoving = existing.map(_.filterNot(e => items.contains(map(e))))
    val removals = needsRemoving.flatMap(r => DBIO.sequence(r.map(i => delete(i).map(_ => i))))

    val needsAdding = existing.map(e => items.toSeq.filterNot(e.map(map).contains))
    val additions = needsAdding.flatMap(a => DBIO.sequence(a.map(comap).map(insert)))

    for {
      removed <- removals
      added <- additions
    } yield (removed, added)
  }

}

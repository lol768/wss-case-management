import akka.Done
import domain.ExtendedPostgresProfile.api._

import scala.concurrent.ExecutionContext

package object services {

  def updateDifferencesDBIO[A, B](items: Set[B], query: Query[Table[A], A, Seq], map: A => B, comap: B => A, insert: Set[A] => DBIO[Seq[A]], delete: Set[A] => DBIO[Seq[Done]])(implicit ec: ExecutionContext): DBIO[Unit] = {
    val existing = query.result

    val needsRemoving = existing.map(_.filterNot(e => items.contains(map(e))))
    val removals = needsRemoving.flatMap(r => delete(r.toSet))

    val needsAdding = existing.map(e => items.toSeq.filterNot(e.map(map).contains))
    val additions = needsAdding.flatMap(a => insert(a.map(comap).toSet))

    DBIO.seq(removals, additions)
  }

}

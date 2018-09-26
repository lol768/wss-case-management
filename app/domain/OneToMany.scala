package domain

object OneToMany {

  def join[T, U](results: Seq[(T, U)])(implicit ord: Ordering[U]): Seq[(T, Seq[U])] =
    results.groupBy { case (one, _) => one }
      .mapValues { values =>
        values.map { case (_, many) => many }.sorted
      }
      .toSeq

  def leftJoin[T, U](results: Seq[(T, Option[U])])(implicit ord: Ordering[U]): Seq[(T, Seq[U])] =
    results.groupBy { case (one, _) => one }
      .mapValues { values =>
        values.flatMap { case (_, many) => many }.sorted
      }
      .toSeq

}

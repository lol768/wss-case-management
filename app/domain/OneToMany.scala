package domain

object OneToMany {

  def leftJoin[T, U](results: Seq[(T, Option[U])]): Seq[(T, Seq[U])] =
    results.groupBy { case (one, many) => one }
      .mapValues { values =>
        values.flatMap { case (one, many) => many }
      }
      .toSeq

}

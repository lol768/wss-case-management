package domain

import slick.lifted.Query
import domain.ExtendedPostgresProfile.api._

import scala.language.higherKinds

object QueryHelpers {

  implicit class QueryHelpers3[
    T1, T2, T3,
    Tbl1 <: Table[T1], Tbl2 <: Table[T2], Tbl3 <: Table[T3],
    S[_]
  ](val q: Query[
    ((Tbl1, Tbl2), Tbl3),
    ((T1, T2), T3),
    S
  ]) extends AnyVal {
    def flattenJoin = q.map { case ((t1, t2), t3) => (t1, t2, t3) }
  }

  implicit class QueryHelpers3Option[
    T1, T2, T3,
    Tbl1 <: Table[T1], Tbl2 <: Table[T2], Tbl3 <: Table[T3],
    S[_]
  ](val q: Query[
    ((Tbl1, Tbl2), Rep[Option[Tbl3]]),
    ((T1, T2), Option[T3]),
    S
  ]) extends AnyVal {
    def flattenJoin = q.map { case ((t1, t2), t3) => (t1, t2, t3) }
  }

  implicit class QueryHelpers4[
    T1, T2, T3, T4,
    Tbl1 <: Table[T1], Tbl2 <: Table[T2], Tbl3 <: Table[T3], Tbl4 <: Table[T4],
    S[_]
  ](val q: Query[
    ((Tbl1, Tbl2, Tbl3), Tbl4),
    ((T1, T2, T3), T4),
    S
    ]) extends AnyVal {
    def flattenJoin = q.map { case ((t1, t2, t3), t4) => (t1, t2, t3, t4) }
  }

  implicit class QueryHelpers2And2[
    T1, T2, T3, T4,
    Tbl1 <: Table[T1], Tbl2 <: Table[T2], Tbl3 <: Table[T3], Tbl4 <: Table[T4],
    S[_]
  ](val q: Query[
    ((Tbl1, Tbl2), (Tbl3, Tbl4)),
    ((T1, T2), (T3, T4)),
    S
    ]) extends AnyVal {
    def flattenJoin = q.map { case ((t1, t2), (t3, t4)) => (t1, t2, t3, t4) }
  }

  implicit class QueryHelpers5[
    T1, T2, T3, T4, T5,
    Tbl1 <: Table[T1], Tbl2 <: Table[T2], Tbl3 <: Table[T3], Tbl4 <: Table[T4], Tbl5 <: Table[T5],
    S[_]
  ](val q: Query[
    ((Tbl1, Tbl2, Tbl3, Tbl4), Tbl5),
    ((T1, T2, T3, T4), T5),
    S
    ]) extends AnyVal {
    def flattenJoin = q.map { case ((t1, t2, t3, t4), t5) => (t1, t2, t3, t4, t5) }
  }

  implicit class QueryHelpers5Nested[
    T1, T2, T3, T4, T5,
    Tbl1 <: Table[T1], Tbl2 <: Table[T2], Tbl3 <: Table[T3], Tbl4 <: Table[T4], Tbl5 <: Table[T5],
    S[_]
  ](val q: Query[
    ((((Tbl1, Tbl2), Tbl3), Tbl4), Tbl5),
    ((((T1, T2), T3), T4), T5),
    S
    ]) extends AnyVal {
    def flattenJoin = q.map { case ((((t1, t2), t3), t4), t5) => (t1, t2, t3, t4, t5) }
  }

  implicit class QueryHelpers5Option[
    T1, T2, T3, T4, T5,
    Tbl1 <: Table[T1], Tbl2 <: Table[T2], Tbl3 <: Table[T3], Tbl4 <: Table[T4], Tbl5 <: Table[T5],
    S[_]
  ](val q: Query[
    ((Tbl1, Tbl2, Tbl3, Tbl4), Rep[Option[Tbl5]]),
    ((T1, T2, T3, T4), Option[T5]),
    S
    ]) extends AnyVal {
    def flattenJoin = q.map { case ((t1, t2, t3, t4), t5) => (t1, t2, t3, t4, t5) }
  }

  implicit class QueryHelpers6Option[
    T1, T2, T3, T4, T5, T6,
    Tbl1 <: Table[T1], Tbl2 <: Table[T2], Tbl3 <: Table[T3], Tbl4 <: Table[T4], Tbl5 <: Table[T5], Tbl6 <: Table[T6],
    S[_]
  ](val q: Query[
    ((Tbl1, Tbl2, Tbl3, Tbl4, Tbl5), Rep[Option[Tbl6]]),
    ((T1, T2, T3, T4, T5), Option[T6]),
    S
    ]) extends AnyVal {
    def flattenJoin = q.map { case ((t1, t2, t3, t4, t5), t6) => (t1, t2, t3, t4, t5, t6) }
  }

}

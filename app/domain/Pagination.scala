package domain

import play.api.data.Form
import play.api.data.Forms.{mapping, number, optional}
import play.api.mvc.Call

import scala.language.higherKinds
import slick.lifted.Query

object Pagination {
  final val DefaultPerPage = 10

  def paginationForm(total: Int, route: Call): Form[Option[Pagination]] = {
    def apply: Option[Int] => Option[Pagination] = (currentPage: Option[Int]) => currentPage.map(Pagination(total, _, route))
    def unapply: Option[Pagination] => Some[Option[Int]] = (pagination: Option[Pagination]) => Some(pagination.map(_.currentPage))
    Form(mapping("page" -> optional(number))(apply)(unapply))
  }

  def asPage(currentPage: Int, itemsPerPage: Int = Pagination.DefaultPerPage): Page =
    Page(currentPage * itemsPerPage, itemsPerPage)

  def firstPage(itemsPerPage: Int = Pagination.DefaultPerPage): Page =
    asPage(0, itemsPerPage)

  implicit class PaginatingQuery[E, U, C[_]](val query: Query[E, U, C]) {
    def paginate(o: Page): Query[E, U, C] = query.drop(o.offset).take(o.maxResults)
  }
}

case class Page(
  offset: Int,
  maxResults: Int
)

case class Pagination(
  total: Int,
  currentPage: Int,
  route: Call,
  itemsPerPage: Int = Pagination.DefaultPerPage,
  paginationParam: String = "page"
) {
  def isFirst: Boolean = currentPage == 0
  def hasNext: Boolean = total > ((currentPage + 1) * itemsPerPage)
  def asPage: Page = Pagination.asPage(currentPage, itemsPerPage)
}

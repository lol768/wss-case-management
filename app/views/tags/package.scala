package views

import domain.SitsProfile
import warwick.sso.UniversityID

package object tags {

  def clientFullName(e: Either[UniversityID, SitsProfile]): String =
    e.fold(_.string, _.fullName)

}

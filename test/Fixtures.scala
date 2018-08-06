import warwick.sso.{UniversityID, Usercode, Users}

object Fixtures {
  object users {
    val noUniId = Users.create(Usercode("nouniid"))
    val studentNewVisitor = Users.create(
      usercode = Usercode("student1"),
      universityId = Some(UniversityID("9900001")),
      student = true,
      undergraduate = true
    )
  }
}

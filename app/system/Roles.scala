package system

import warwick.sso.RoleName

object Roles {
  val Admin = RoleName("admin")
  val Masquerader = RoleName("masqueraders")
  val Sysadmin = RoleName("sysadmin")
  val ReportingAdmin = RoleName("reporting-admin")
  val APIRead = RoleName("api-read")
  val APIWrite = RoleName("api-write")
}

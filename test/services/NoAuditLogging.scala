package services

trait NoAuditLogging {

  implicit val auditLogContext: AuditLogContext = AuditLogContext.empty()

}

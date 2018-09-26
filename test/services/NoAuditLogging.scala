package services

trait NoAuditLogging extends NoTimeTracking {

  implicit val auditLogContext: AuditLogContext = AuditLogContext.empty()(timingContext)

}

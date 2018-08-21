# --- !Ups
ALTER TABLE client_summary RENAME COLUMN version TO version_utc;
ALTER TABLE client_summary_version RENAME COLUMN version TO version_utc;
ALTER TABLE client_summary_version RENAME COLUMN version_timestamp TO version_timestamp_utc;
ALTER TABLE enquiry RENAME COLUMN version TO version_utc;
ALTER TABLE enquiry_version RENAME COLUMN version TO version_utc;
ALTER TABLE enquiry_version RENAME COLUMN version_timestamp TO version_timestamp_utc;
ALTER TABLE message RENAME COLUMN version TO version_utc;
ALTER TABLE message_version RENAME COLUMN version TO version_utc;
ALTER TABLE message_version RENAME COLUMN version_timestamp TO version_timestamp_utc;
ALTER TABLE outgoing_email RENAME COLUMN created_at TO created_utc;
ALTER TABLE outgoing_email RENAME COLUMN sent_at TO sent_at_utc;
ALTER TABLE outgoing_email RENAME COLUMN last_send_attempt_at TO last_send_attempt_at_utc;
ALTER TABLE outgoing_email RENAME COLUMN version TO version_utc;
ALTER TABLE outgoing_email_version RENAME COLUMN created_at TO created_utc;
ALTER TABLE outgoing_email_version RENAME COLUMN sent_at TO sent_at_utc;
ALTER TABLE outgoing_email_version RENAME COLUMN last_send_attempt_at TO last_send_attempt_at_utc;
ALTER TABLE outgoing_email_version RENAME COLUMN version TO version_utc;
ALTER TABLE outgoing_email_version RENAME COLUMN version_timestamp TO version_timestamp_utc;
ALTER TABLE user_registration RENAME COLUMN version TO version_utc;
ALTER TABLE user_registration_version RENAME COLUMN version TO version_utc;
ALTER TABLE user_registration_version RENAME COLUMN version_timestamp TO version_timestamp_utc;

# --- !Downs
ALTER TABLE client_summary RENAME COLUMN version_utc TO version;
ALTER TABLE client_summary_version RENAME COLUMN version_utc TO version;
ALTER TABLE client_summary_version RENAME COLUMN version_timestamp_utc TO version_timestamp;
ALTER TABLE enquiry RENAME COLUMN version_utc TO version;
ALTER TABLE enquiry_version RENAME COLUMN version_utc TO version;
ALTER TABLE enquiry_version RENAME COLUMN version_timestamp_utc TO version_timestamp;
ALTER TABLE message RENAME COLUMN version_utc TO version;
ALTER TABLE message_version RENAME COLUMN version_utc TO version;
ALTER TABLE message_version RENAME COLUMN version_timestamp_utc TO version_timestamp;
ALTER TABLE outgoing_email RENAME COLUMN created_utc TO created_at;
ALTER TABLE outgoing_email RENAME COLUMN sent_at_utc TO sent_at;
ALTER TABLE outgoing_email RENAME COLUMN last_send_attempt_at_utc TO last_send_attempt_at;
ALTER TABLE outgoing_email RENAME COLUMN version_utc TO version;
ALTER TABLE outgoing_email_version RENAME COLUMN created_utc TO created_at;
ALTER TABLE outgoing_email_version RENAME COLUMN sent_at_utc TO sent_at;
ALTER TABLE outgoing_email_version RENAME COLUMN last_send_attempt_at_utc TO last_send_attempt_at;
ALTER TABLE outgoing_email_version RENAME COLUMN version_utc TO version;
ALTER TABLE outgoing_email_version RENAME COLUMN version_timestamp_utc TO version_timestamp;
ALTER TABLE user_registration RENAME COLUMN version_utc TO version;
ALTER TABLE user_registration_version RENAME COLUMN version_utc TO version;
ALTER TABLE user_registration_version RENAME COLUMN version_timestamp_utc TO version_timestamp;
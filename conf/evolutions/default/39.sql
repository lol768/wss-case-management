# --- !Ups

ALTER TABLE enquiry_version ADD COLUMN version_user VARCHAR(100) NULL;
ALTER TABLE enquiry_note_version ADD COLUMN version_user VARCHAR(100) NULL;
ALTER TABLE message_version ADD COLUMN version_user VARCHAR(100) NULL;
ALTER TABLE owner_version ADD COLUMN version_user VARCHAR(100) NULL;
ALTER TABLE appointment_version ADD COLUMN version_user VARCHAR(100) NULL;
ALTER TABLE appointment_client_version ADD COLUMN version_user VARCHAR(100) NULL;
ALTER TABLE client_case_version ADD COLUMN version_user VARCHAR(100) NULL;
ALTER TABLE client_case_tag_version ADD COLUMN version_user VARCHAR(100) NULL;
ALTER TABLE client_case_client_version ADD COLUMN version_user VARCHAR(100) NULL;
ALTER TABLE client_case_link_version ADD COLUMN version_user VARCHAR(100) NULL;
ALTER TABLE client_case_note_version ADD COLUMN version_user VARCHAR(100) NULL;
ALTER TABLE client_case_document_version ADD COLUMN version_user VARCHAR(100) NULL;
ALTER TABLE client_summary_version ADD COLUMN version_user VARCHAR(100) NULL;
ALTER TABLE reasonable_adjustment_version ADD COLUMN version_user VARCHAR(100) NULL;
ALTER TABLE outgoing_email_version ADD COLUMN version_user VARCHAR(100) NULL;
ALTER TABLE user_registration_version ADD COLUMN version_user VARCHAR(100) NULL;
ALTER TABLE uploaded_file_version ADD COLUMN version_user VARCHAR(100) NULL;

# --- !Downs

ALTER TABLE enquiry_version DROP COLUMN version_user;
ALTER TABLE enquiry_note_version DROP COLUMN version_user;
ALTER TABLE message_version DROP COLUMN version_user;
ALTER TABLE owner_version DROP COLUMN version_user;
ALTER TABLE appointment_version DROP COLUMN version_user;
ALTER TABLE appointment_client_version DROP COLUMN version_user;
ALTER TABLE client_case_version DROP COLUMN version_user;
ALTER TABLE client_case_tag_version DROP COLUMN version_user;
ALTER TABLE client_case_client_version DROP COLUMN version_user;
ALTER TABLE client_case_link_version DROP COLUMN version_user;
ALTER TABLE client_case_note_version DROP COLUMN version_user;
ALTER TABLE client_case_document_version DROP COLUMN version_user;
ALTER TABLE client_summary_version DROP COLUMN version_user;
ALTER TABLE reasonable_adjustment_version DROP COLUMN version_user;
ALTER TABLE outgoing_email_version DROP COLUMN version_user;
ALTER TABLE user_registration_version DROP COLUMN version_user;
ALTER TABLE uploaded_file_version DROP COLUMN version_user;

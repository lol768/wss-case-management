# --- !Ups

ALTER TABLE client_summary ADD COLUMN notes text;
ALTER TABLE client_summary ADD COLUMN alt_contact_number VARCHAR(20);
ALTER TABLE client_summary ADD COLUMN alt_email VARCHAR(100);
ALTER TABLE client_summary ADD COLUMN risk_status VARCHAR(10);

ALTER TABLE client_summary_version ADD COLUMN notes text;
ALTER TABLE client_summary_version ADD COLUMN alt_contact_number VARCHAR(20);
ALTER TABLE client_summary_version ADD COLUMN alt_email VARCHAR(100);
ALTER TABLE client_summary_version ADD COLUMN risk_status VARCHAR(10);

CREATE TABLE reasonable_adjustment (
  university_id VARCHAR(20) NOT NULL,
  reasonable_adjustment VARCHAR(20) NOT NULL,
  version_utc TIMESTAMP(3) NOT NULL,
  CONSTRAINT pk_reasonable_adjustment PRIMARY KEY (university_id, reasonable_adjustment),
  CONSTRAINT fk_reasonable_adjustment FOREIGN KEY (university_id) REFERENCES client_summary (university_id) ON DELETE RESTRICT
);

CREATE INDEX idx_reasonable_adjustment ON reasonable_adjustment (university_id);

CREATE TABLE reasonable_adjustment_version (
  university_id VARCHAR(20) NOT NULL,
  reasonable_adjustment VARCHAR(20) NOT NULL,
  version_utc TIMESTAMP(3) NOT NULL,
  version_operation CHAR(6) NOT NULL,
  version_timetsamp_utc TIMESTAMP(3) NOT NULL,
  CONSTRAINT pk_reasonable_adjustment_version PRIMARY KEY (university_id, reasonable_adjustment, version_timetsamp_utc)
);

CREATE INDEX idx_reasonable_adjustment_version ON reasonable_adjustment_version (university_id, reasonable_adjustment, version_utc);

# --- !Downs

DROP TABLE reasonable_adjustment;
DROP TABLE reasonable_adjustment_version;
ALTER TABLE client_summary DROP COLUMN notes;
ALTER TABLE client_summary DROP COLUMN alt_contact_number;
ALTER TABLE client_summary DROP COLUMN alt_email;
ALTER TABLE client_summary DROP COLUMN risk_status;
ALTER TABLE client_summary_version DROP COLUMN notes;
ALTER TABLE client_summary_version DROP COLUMN alt_contact_number;
ALTER TABLE client_summary_version DROP COLUMN alt_email;
ALTER TABLE client_summary_version DROP COLUMN risk_status;

# --- !Ups

-- Data migration for 36
-- In a separate file due to use of Postgres-specific JSON operators

UPDATE client_summary SET
  notes = data::json ->> 'notes',
  alt_contact_number = data::json ->> 'alternativeContactNumber',
  alt_email = data::json ->> 'alternativeEmailAddress',
  risk_status = data::json ->> 'riskStatus';

INSERT INTO reasonable_adjustment
  SELECT university_id, value, current_timestamp
  FROM client_summary, json_array_elements_text(client_summary.data :: json -> 'reasonableAdjustments');

ALTER TABLE client_summary
  DROP COLUMN data;

ALTER TABLE client_summary_version
  DROP COLUMN data;

# --- !Downs

TRUNCATE reasonable_adjustment;

ALTER TABLE client_summary
  ADD COLUMN data TEXT;
ALTER TABLE client_summary_version
  ADD COLUMN data TEXT;

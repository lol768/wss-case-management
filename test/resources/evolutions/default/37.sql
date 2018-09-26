# --- !Ups

-- Data migration for 36
-- In a separate file due to use of Postgres-specific JSON operators in non-test version

ALTER TABLE client_summary
  DROP COLUMN data;

ALTER TABLE client_summary_version
  DROP COLUMN data;

# --- !Downs

ALTER TABLE client_summary
  ADD COLUMN data TEXT;
ALTER TABLE client_summary_version
  ADD COLUMN data TEXT;

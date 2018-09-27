# --- !Ups

ALTER TABLE reasonable_adjustment_version RENAME COLUMN version_timetsamp_utc TO version_timestamp_utc;

# --- !Downs

ALTER TABLE reasonable_adjustment_version RENAME COLUMN version_timestamp_utc TO version_timetsamp_utc;


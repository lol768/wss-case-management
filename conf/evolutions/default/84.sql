# --- !Ups
alter table audit_event alter column data type jsonb using data::jsonb;
alter table outgoing_email alter column email type jsonb using email::jsonb;
alter table outgoing_email_version alter column email type jsonb using email::jsonb;
alter table user_preferences alter column preferences type jsonb using preferences::jsonb;
alter table user_preferences_version alter column preferences type jsonb using preferences::jsonb;
alter table user_registration alter column data type jsonb using data::jsonb;
alter table user_registration_version alter column data type jsonb using data::jsonb;
alter table client_summary add column initial_consultation jsonb;
alter table client_summary_version add column initial_consultation jsonb;

# --- !Downs
alter table audit_event alter column data type text;
alter table outgoing_email alter column email type text;
alter table outgoing_email_version alter column email type text;
alter table user_preferences alter column preferences type text;
alter table user_preferences_version alter column preferences type text;
alter table user_registration alter column data type text;
alter table user_registration_version alter column data type text;
alter table client_summary drop column initial_consultation;
alter table client_summary_version drop column initial_consultation;

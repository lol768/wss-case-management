# --- !Ups

alter table dsa_application alter column application_date_utc set data type date;
alter table dsa_application_version alter column application_date_utc set data type date;
alter table dsa_application alter column confirmation_date_utc set data type date;
alter table dsa_application_version alter column confirmation_date_utc set data type date;

# --- !Downs

alter table dsa_application alter column application_date_utc set data type timestamp(3);
alter table dsa_application_version alter column application_date_utc set data type timestamp(3);
alter table dsa_application alter column confirmation_date_utc set data type timestamp(3);
alter table dsa_application_version alter column confirmation_date_utc set data type timestamp(3);

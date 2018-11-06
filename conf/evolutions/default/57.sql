# --- !Ups
alter table appointment add outcome VARCHAR(100);
alter table appointment_version add outcome VARCHAR(100);

# --- !Downs
alter table appointment drop column outcome;
alter table appointment_version drop column outcome;
# --- !Ups
alter table appointment add column cancellation_reason VARCHAR(20);
alter table appointment_version add column cancellation_reason VARCHAR(20);

# --- !Downs
alter table appointment drop column cancellation_reason;
alter table appointment_version drop column cancellation_reason;

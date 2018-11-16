# --- !Ups
alter table appointment_client add attendance_state VARCHAR(100) null;
alter table appointment_client_version add attendance_state VARCHAR(100) null;

# --- !Downs
alter table appointment_client drop attendance_state;
alter table appointment_client_version drop attendance_state;

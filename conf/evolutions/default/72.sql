# --- !Ups
alter table appointment add dsa_support_accessed VARCHAR(100) null;
alter table appointment_version add dsa_support_accessed VARCHAR(100) null;

# --- !Downs
alter table appointment drop dsa_support_accessed;
alter table appointment_version drop dsa_support_accessed;

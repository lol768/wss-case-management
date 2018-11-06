# --- !Ups
alter table appointment add outcome VARCHAR(100);
alter table appointment_version add outcome VARCHAR(100);

# --- !Downs
alter table appointment drop column outcome;
alter table appointment_version drop column outcome;

# --- !Ups
ALTER TABLE USER_REGISTRATION ADD COLUMN LAST_INVITED_UTC timestamp(3) DEFAULT current_date NOT NULL;
ALTER TABLE USER_REGISTRATION_VERSION ADD COLUMN LAST_INVITED_UTC timestamp(3) DEFAULT current_date NOT NULL;

# --- !Downs
ALTER TABLE USER_REGISTRATION DROP COLUMN LAST_INVITED_UTC;
ALTER TABLE USER_REGISTRATION_VERSION DROP COLUMN LAST_INVITED_UTC;

# --- !Ups
ALTER TABLE AUDIT_EVENT ALTER COLUMN ID TYPE uuid;
ALTER TABLE USER_REGISTRATION ALTER COLUMN ID TYPE uuid;

# --- !Downs
ALTER TABLE AUDIT_EVENT ALTER COLUMN ID TYPE varchar(100);
ALTER TABLE USER_REGISTRATION ALTER COLUMN ID TYPE varchar(100);
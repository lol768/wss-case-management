# --- !Ups
ALTER TABLE CLIENT_CASE ADD COLUMN SUBJECT varchar(200);
UPDATE CLIENT_CASE SET SUBJECT = 'Case' WHERE SUBJECT IS NULL;
ALTER TABLE CLIENT_CASE ALTER COLUMN SUBJECT SET NOT NULL;
ALTER TABLE CLIENT_CASE_VERSION ADD COLUMN SUBJECT varchar(200);
UPDATE CLIENT_CASE_VERSION SET SUBJECT = 'Case' WHERE SUBJECT IS NULL;
ALTER TABLE CLIENT_CASE_VERSION ALTER COLUMN SUBJECT SET NOT NULL;

# --- !Downs
ALTER TABLE CLIENT_CASE DROP COLUMN SUBJECT;
ALTER TABLE CLIENT_CASE_VERSION DROP COLUMN SUBJECT;
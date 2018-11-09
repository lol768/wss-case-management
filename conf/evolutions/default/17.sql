# --- !Ups
ALTER TABLE ENQUIRY ADD COLUMN SUBJECT varchar(200);
UPDATE ENQUIRY SET SUBJECT = 'Enquiry' WHERE SUBJECT IS NULL;
ALTER TABLE ENQUIRY ALTER COLUMN SUBJECT SET NOT NULL;
ALTER TABLE ENQUIRY_VERSION ADD COLUMN SUBJECT varchar(200);
UPDATE ENQUIRY_VERSION SET SUBJECT = 'Enquiry' WHERE SUBJECT IS NULL;
ALTER TABLE ENQUIRY_VERSION ALTER COLUMN SUBJECT SET NOT NULL;

# --- !Downs
ALTER TABLE ENQUIRY DROP COLUMN SUBJECT;
ALTER TABLE ENQUIRY_VERSION DROP COLUMN SUBJECT;
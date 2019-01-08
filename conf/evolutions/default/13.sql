# --- !Ups
ALTER TABLE ENQUIRY ADD COLUMN CREATED_UTC timestamp(3);
UPDATE ENQUIRY SET CREATED_UTC = VERSION_UTC WHERE CREATED_UTC IS NULL;
ALTER TABLE ENQUIRY ALTER COLUMN CREATED_UTC SET NOT NULL;
ALTER TABLE ENQUIRY_VERSION ADD COLUMN CREATED_UTC timestamp(3);
UPDATE ENQUIRY_VERSION SET CREATED_UTC = VERSION_UTC WHERE CREATED_UTC IS NULL;
ALTER TABLE ENQUIRY_VERSION ALTER COLUMN CREATED_UTC SET NOT NULL;

# --- !Downs
ALTER TABLE ENQUIRY DROP COLUMN CREATED_UTC;
ALTER TABLE ENQUIRY_VERSION DROP COLUMN CREATED_UTC;
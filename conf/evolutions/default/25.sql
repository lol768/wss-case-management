# --- !Ups
CREATE SEQUENCE SEQ_ENQUIRY_KEY START WITH 1000 INCREMENT BY 1;

ALTER TABLE ENQUIRY ADD COLUMN ENQUIRY_KEY varchar(10) DEFAULT 'ENQ-' || nextval('SEQ_ENQUIRY_KEY');
ALTER TABLE ENQUIRY_VERSION ADD COLUMN ENQUIRY_KEY varchar(10);

UPDATE ENQUIRY_VERSION SET ENQUIRY_KEY = (SELECT ENQUIRY_KEY FROM ENQUIRY WHERE ID = ENQUIRY_VERSION.ID);

ALTER TABLE ENQUIRY ALTER COLUMN ENQUIRY_KEY DROP DEFAULT;
ALTER TABLE ENQUIRY ALTER COLUMN ENQUIRY_KEY SET NOT NULL;
ALTER TABLE ENQUIRY_VERSION ALTER COLUMN ENQUIRY_KEY SET NOT NULL;

CREATE UNIQUE INDEX IDX_ENQUIRY_KEY ON ENQUIRY (ENQUIRY_KEY);

# --- !Downs
DROP SEQUENCE SEQ_ENQUIRY_KEY;

ALTER TABLE ENQUIRY DROP COLUMN ENQUIRY_KEY;
ALTER TABLE ENQUIRY_VERSION DROP COLUMN ENQUIRY_KEY;
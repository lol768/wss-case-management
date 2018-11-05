# --- !Ups
CREATE TABLE DSA_APPLICATION (
  ID UUID NOT NULL,
  APPLICATION_DATE_UTC TIMESTAMP (3),
  FUNDING_APPROVED BOOLEAN,
  CONFIRMATION_DATE_UTC TIMESTAMP (3),
  INELIGIBILITY_REASON VARCHAR (100),
  VERSION_UTC timestamp(3) NOT NULL,
  CONSTRAINT PK_DSA_APPLICATION PRIMARY KEY (ID)
);

ALTER TABLE CLIENT_CASE ADD COLUMN DSA_APPLICATION UUID;
ALTER TABLE CLIENT_CASE ADD CONSTRAINT FK_DSA_APPLICATION FOREIGN KEY (DSA_APPLICATION) REFERENCES DSA_APPLICATION (ID) DEFERRABLE INITIALLY DEFERRED;

CREATE TABLE DSA_APPLICATION_VERSION(
  ID UUID NOT NULL,
  APPLICATION_DATE_UTC TIMESTAMP(3),
  FUNDING_APPROVED BOOLEAN,
  CONFIRMATION_DATE_UTC TIMESTAMP(3),
  INELIGIBILITY_REASON VARCHAR(100),
  VERSION_UTC timestamp(3) NOT NULL,
  VERSION_OPERATION char(6) NOT NULL,
  VERSION_TIMESTAMP_UTC timestamp(3) NOT NULL,
  VERSION_USER VARCHAR(100) NULL,
  CONSTRAINT PK_VERSION_DSA_APPLICATION PRIMARY KEY (ID, VERSION_TIMESTAMP_UTC)
);

CREATE TABLE DSA_FUNDING_TYPE (
  DSA_APPLICATION_ID UUID NOT NULL,
  FUNDING_TYPE VARCHAR(100) NOT NULL,
  VERSION_UTC timestamp(3) NOT NULL,
  CONSTRAINT PK_DSA_FUNDING_TYPE PRIMARY KEY (DSA_APPLICATION_ID, FUNDING_TYPE),
  CONSTRAINT FK_DSA_FUNDING_TYPE FOREIGN KEY (DSA_APPLICATION_ID) REFERENCES DSA_APPLICATION (ID) DEFERRABLE INITIALLY DEFERRED
);

CREATE INDEX IDX_DSA_FUNDING_TYPE ON DSA_FUNDING_TYPE (DSA_APPLICATION_ID);

CREATE TABLE DSA_FUNDING_TYPE_VERSION (
  DSA_APPLICATION_ID UUID NOT NULL,
  FUNDING_TYPE VARCHAR(100) NOT NULL,
  VERSION_UTC timestamp(3) NOT NULL,
  VERSION_OPERATION char(6) NOT NULL,
  VERSION_TIMESTAMP_UTC timestamp(3) NOT NULL,
  VERSION_USER VARCHAR(100) NULL,
  CONSTRAINT PK_DSA_FUNDING_TYPE_VERSION PRIMARY KEY (DSA_APPLICATION_ID, FUNDING_TYPE, VERSION_TIMESTAMP_UTC)
);

CREATE INDEX IDX_DSA_FUNDING_TYPE_VERSION ON DSA_FUNDING_TYPE_VERSION (DSA_APPLICATION_ID, FUNDING_TYPE, VERSION_UTC);

ALTER TABLE CLIENT_CASE_VERSION ADD COLUMN DSA_APPLICATION UUID;

# --- !Downs
ALTER TABLE CLIENT_CASE_VERSION DROP COLUMN DSA_APPLICATION;
ALTER TABLE CLIENT_CASE DROP COLUMN DSA_APPLICATION;

DROP TABLE DSA_FUNDING_TYPE_VERSION;
DROP TABLE DSA_FUNDING_TYPE;

DROP TABLE DSA_APPLICATION_VERSION;
DROP TABLE DSA_APPLICATION;

# --- !Ups
alter table appointment add outcome VARCHAR(100);
alter table appointment_version add outcome VARCHAR(100);

# --- !Downs
alter table appointment drop column outcome;
alter table appointment_version drop column outcome;
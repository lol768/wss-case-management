# --- !Ups
CREATE TABLE APPOINTMENT_CASE (
  ID UUID NOT NULL,
  APPOINTMENT_ID UUID NOT NULL,
  CASE_ID UUID NOT NULL,
  TEAM_MEMBER varchar(100) NOT NULL,
  VERSION_UTC TIMESTAMP(3) NOT NULL,
  CONSTRAINT PK_APPOINTMENT_CASE PRIMARY KEY (ID),
  CONSTRAINT APPOINTMENT_CASE_UNIQUE UNIQUE (APPOINTMENT_ID, CASE_ID),
  CONSTRAINT FK_APPOINTMENT_CASE_APPOINTMENT FOREIGN KEY (APPOINTMENT_ID) REFERENCES APPOINTMENT (ID) ON DELETE CASCADE,
  CONSTRAINT FK_APPOINTMENT_CASE_CASE FOREIGN KEY (CASE_ID) REFERENCES CLIENT_CASE (ID) ON DELETE CASCADE
);

CREATE TABLE APPOINTMENT_CASE_VERSION (
  ID UUID NOT NULL,
  APPOINTMENT_ID UUID NOT NULL,
  CASE_ID UUID NOT NULL,
  TEAM_MEMBER varchar(100) NOT NULL,
  VERSION_UTC TIMESTAMP(3) NOT NULL,
  VERSION_OPERATION CHAR(6) NOT NULL,
  VERSION_TIMESTAMP_UTC TIMESTAMP(3) NOT NULL,
  VERSION_USER VARCHAR(100),
  CONSTRAINT PK_APPOINTMENT_CASE_VERSION PRIMARY KEY (ID, VERSION_TIMESTAMP_UTC),
  CONSTRAINT APPOINTMENT_CASE_UNIQUE_VERSION UNIQUE (APPOINTMENT_ID, CASE_ID, VERSION_TIMESTAMP_UTC)
);

ALTER TABLE APPOINTMENT DROP COLUMN CASE_ID;
ALTER TABLE APPOINTMENT_VERSION DROP COLUMN CASE_ID;

# --- !Downs
DROP TABLE APPOINTMENT_CASE;
DROP TABLE APPOINTMENT_CASE_VERSION;

ALTER TABLE APPOINTMENT ADD COLUMN CASE_ID UUID;
ALTER TABLE APPOINTMENT ADD CONSTRAINT FK_APPOINTMENT_CASE FOREIGN KEY (CASE_ID) REFERENCES CLIENT_CASE (ID) ON DELETE RESTRICT;
CREATE INDEX IDX_APPOINTMENT_CASE ON APPOINTMENT (CASE_ID);

ALTER TABLE APPOINTMENT_VERSION ADD COLUMN CASE_ID UUID;
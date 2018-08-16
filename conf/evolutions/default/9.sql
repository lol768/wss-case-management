# --- !Ups

DROP TABLE USER_REGISTRATION;

CREATE TABLE USER_REGISTRATION (
  UNIVERSITY_ID varchar(10) NOT NULL,
  DATA text,
  VERSION timestamp(3) NOT NULL,
  CONSTRAINT PK_USER_REGISTRATION PRIMARY KEY (UNIVERSITY_ID)
);

CREATE TABLE USER_REGISTRATION_VERSION (
  UNIVERSITY_ID varchar(10) NOT NULL,
  DATA text,
  VERSION timestamp(3) NOT NULL,
  VERSION_OPERATION char(6) NOT NULL,
  VERSION_TIMESTAMP timestamp(3) NOT NULL,
  CONSTRAINT PK_USER_REGISTRATION_VERSION PRIMARY KEY (UNIVERSITY_ID, VERSION_TIMESTAMP)
);

# --- !Downs

DROP TABLE USER_REGISTRATION;
DROP TABLE USER_REGISTRATION_VERSION;
CREATE TABLE USER_REGISTRATION (
  ID varchar(100) NOT NULL,
  UNIVERSITY_ID varchar(10) NOT NULL,
  UPDATED_DATE_UTC timestamp NOT NULL,
  TEAM_ID varchar(20) NOT NULL,
  DATA text
);

CREATE INDEX IDX_USER_REGISTRATION_UNI_ID ON USER_REGISTRATION (UNIVERSITY_ID);
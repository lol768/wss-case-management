# --- !Ups
CREATE TABLE USER_REGISTRATION (
	UNIVERSITY_ID varchar(10) NOT NULL,
  UPDATED_DATE timestamp NOT NULL,
  TEAM_ID varchar(20) NOT NULL,
  DATA text
);

CREATE INDEX IDX_USER_REGISTRATION_UNI_ID ON USER_REGISTRATION (UNIVERSITY_ID);

# --- !Downs
DROP TABLE USER_REGISTRATION;
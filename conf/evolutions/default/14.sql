# --- !Ups
CREATE INDEX IDX_ENQUIRY_UNIVERSITYID ON ENQUIRY (UNIVERSITY_ID);
CREATE INDEX IDX_ENQUIRY_TEAM ON ENQUIRY (TEAM_ID);
CREATE INDEX IDX_ENQUIRY_STATE ON ENQUIRY (STATE);

# --- !Downs
DROP INDEX IDX_ENQUIRY_UNIVERSITYID;
DROP INDEX IDX_ENQUIRY_TEAM;
DROP INDEX IDX_ENQUIRY_STATE;
# --- !Ups
CREATE INDEX IDX_MESSAGE_CLIENT_UNIVERSITYID ON MESSAGE_CLIENT (UNIVERSITY_ID);
CREATE INDEX IDX_MESSAGE_OWNER ON MESSAGE (OWNER_ID, OWNER_TYPE);

# --- !Downs
DROP INDEX IDX_MESSAGE_CLIENT_UNIVERSITYID;
DROP INDEX IDX_MESSAGE_OWNER;
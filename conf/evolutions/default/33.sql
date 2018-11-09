# --- !Ups

-- Migrate many-to-1 message clients into a column, because in practice it's
-- now one client per message everywhere.

-- In production this would be a risky migration because of the potential
-- for a message_client to be created on the old table by old nodes. We would
-- have to be much more careful and probably keep message_client until the next
-- deploy.

ALTER TABLE MESSAGE
  ADD COLUMN UNIVERSITY_ID VARCHAR(10);
UPDATE MESSAGE SET UNIVERSITY_ID =
  (SELECT C.UNIVERSITY_ID FROM MESSAGE_CLIENT C WHERE MESSAGE_ID = MESSAGE.ID);
ALTER TABLE MESSAGE
  ALTER COLUMN UNIVERSITY_ID SET NOT NULL;

ALTER TABLE MESSAGE_VERSION
  ADD COLUMN UNIVERSITY_ID VARCHAR(10);
UPDATE MESSAGE_VERSION SET UNIVERSITY_ID =
  (SELECT C.UNIVERSITY_ID FROM MESSAGE_CLIENT C WHERE MESSAGE_ID = MESSAGE_VERSION.ID);
ALTER TABLE MESSAGE_VERSION
  ALTER COLUMN UNIVERSITY_ID SET NOT NULL;


DROP TABLE MESSAGE_CLIENT;

# --- !Downs

TRUNCATE MESSAGE CASCADE;
CREATE TABLE MESSAGE_CLIENT (
  ID uuid NOT NULL,
  UNIVERSITY_ID varchar(20),
  MESSAGE_ID uuid NOT NULL,
  CONSTRAINT PK_MESSAGE_RECIPIENT PRIMARY KEY (ID)
);

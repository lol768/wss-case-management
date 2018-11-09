# --- !Ups
CREATE TABLE CLIENT_CASE_LINK (
  LINK_TYPE VARCHAR(100) NOT NULL,
  OUTGOING_CASE_ID uuid NOT NULL,
  INCOMING_CASE_ID uuid NOT NULL,
  VERSION_UTC timestamp(3) NOT NULL,
  CONSTRAINT PK_CASE_LINK PRIMARY KEY (LINK_TYPE, OUTGOING_CASE_ID, INCOMING_CASE_ID),
  CONSTRAINT FK_CASE_LINK_OUTGOING FOREIGN KEY (OUTGOING_CASE_ID) REFERENCES CLIENT_CASE (ID) ON DELETE RESTRICT,
  CONSTRAINT FK_CASE_LINK_INCOMING FOREIGN KEY (INCOMING_CASE_ID) REFERENCES CLIENT_CASE (ID) ON DELETE RESTRICT
);

CREATE INDEX IDX_CASE_LINK_OUTGOING ON CLIENT_CASE_LINK (OUTGOING_CASE_ID);
CREATE INDEX IDX_CASE_LINK_INCOMING ON CLIENT_CASE_LINK (INCOMING_CASE_ID);

CREATE TABLE CLIENT_CASE_LINK_VERSION (
  LINK_TYPE VARCHAR(100) NOT NULL,
  OUTGOING_CASE_ID uuid NOT NULL,
  INCOMING_CASE_ID uuid NOT NULL,
  VERSION_UTC timestamp(3) NOT NULL,
  VERSION_OPERATION char(6) NOT NULL,
  VERSION_TIMESTAMP_UTC timestamp(3) NOT NULL,
  CONSTRAINT PK_CASE_LINK_VERSION PRIMARY KEY (LINK_TYPE, OUTGOING_CASE_ID, INCOMING_CASE_ID, VERSION_TIMESTAMP_UTC)
);

CREATE INDEX IDX_CASE_LINK_VERSION ON CLIENT_CASE_LINK_VERSION (LINK_TYPE, OUTGOING_CASE_ID, INCOMING_CASE_ID, VERSION_UTC);

# --- !Downs
DROP TABLE CLIENT_CASE_LINK_VERSION;
DROP TABLE CLIENT_CASE_LINK;
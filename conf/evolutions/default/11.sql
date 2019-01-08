# --- !Ups
create table OUTGOING_EMAIL (
  ID uuid not null,
  CREATED_AT timestamp(3) not null,
  EMAIL text,
  RECIPIENT varchar(255),
  RECIPIENT_EMAIL varchar(1000),
  SENT_AT timestamp(3),
  LAST_SEND_ATTEMPT_AT timestamp(3),
  FAILURE_REASON text,
  VERSION timestamp(3) NOT NULL,
  constraint PK_OUTGOING_EMAIL primary key (ID)
);

create table OUTGOING_EMAIL_VERSION (
  ID uuid not null,
  CREATED_AT timestamp(3) not null,
  EMAIL text,
  RECIPIENT varchar(255),
  RECIPIENT_EMAIL varchar(1000),
  SENT_AT timestamp(3),
  LAST_SEND_ATTEMPT_AT timestamp(3),
  FAILURE_REASON text,
  VERSION timestamp(3) NOT NULL,
  VERSION_OPERATION char(6) NOT NULL,
  VERSION_TIMESTAMP timestamp(3) NOT NULL,
  CONSTRAINT PK_OUTGOING_EMAIL_VERSION PRIMARY KEY (ID, VERSION_TIMESTAMP)
);

CREATE INDEX IDX_OUTGOING_EMAIL_VERSION ON OUTGOING_EMAIL_VERSION (ID, VERSION);

# --- !Downs

DROP TABLE OUTGOING_EMAIL;
DROP TABLE OUTGOING_EMAIL_VERSION;
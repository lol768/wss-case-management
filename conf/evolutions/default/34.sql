# --- !Ups
create sequence seq_appointment_key start with 1000 increment by 1;

create table appointment (
  id UUID NOT NULL,
  appointment_key VARCHAR(10) NOT NULL,
  case_id UUID,
  subject VARCHAR(200) NOT NULL,
  start_utc TIMESTAMP(3) NOT NULL,
  duration_secs INTEGER NOT NULL,
  location VARCHAR(200),
  team_id VARCHAR(20) NOT NULL,
  team_member VARCHAR(100) NOT NULL,
  appointment_type VARCHAR(20) NOT NULL,
  created_utc TIMESTAMP(3) NOT NULL,
  version_utc TIMESTAMP(3) NOT NULL,
  constraint pk_appointment primary key (id),
  constraint fk_appointment_case foreign key (case_id) references client_case (id) on delete restrict
);

create index idx_appointment_case on appointment (case_id);
create unique index idx_appointment_key on appointment (appointment_key);
create index idx_appointment_team on appointment (start_utc,team_id);
create index idx_appointment_team_member on appointment (start_utc,team_member);

create table appointment_version (
  id UUID NOT NULL,
  appointment_key VARCHAR(10) NOT NULL,
  case_id UUID,
  subject VARCHAR(200) NOT NULL,
  start_utc TIMESTAMP(3) NOT NULL,
  duration_secs INTEGER NOT NULL,
  location VARCHAR(200),
  team_id VARCHAR(20) NOT NULL,
  team_member VARCHAR(100) NOT NULL,
  appointment_type VARCHAR(20) NOT NULL,
  created_utc TIMESTAMP(3) NOT NULL,
  version_utc TIMESTAMP(3) NOT NULL,
  version_operation CHAR(6) NOT NULL,
  version_timestamp_utc TIMESTAMP(3) NOT NULL,
  constraint pk_appointment_version primary key (id, version_timestamp_utc)
);

create index idx_appointment_version on appointment_version (id,version_utc);

create table appointment_client (
  university_id VARCHAR(10) NOT NULL,
  appointment_id UUID NOT NULL,
  state VARCHAR(20) NOT NULL,
  cancellation_reason VARCHAR(20),
  created_utc TIMESTAMP(3) NOT NULL,
  version_utc TIMESTAMP(3) NOT NULL,
  constraint pk_appointment_client primary key (university_id, appointment_id),
  constraint fk_appointment_client foreign key (appointment_id) references appointment (id) on delete restrict
);

create table appointment_client_version (
  university_id VARCHAR(10) NOT NULL,
  appointment_id UUID NOT NULL,
  state VARCHAR(20) NOT NULL,
  cancellation_reason VARCHAR(20),
  created_utc TIMESTAMP(3) NOT NULL,
  version_utc TIMESTAMP(3) NOT NULL,
  version_operation CHAR(6) NOT NULL,
  version_timestamp_utc TIMESTAMP(3) NOT NULL,
  constraint pk_appointment_client_version primary key (university_id, appointment_id, version_timestamp_utc)
);

create index idx_appointment_client_version on appointment_client_version (university_id, appointment_id, version_utc);

# --- !Downs
drop sequence seq_appointment_key;
drop table appointment_client;
drop table appointment_client_version;
drop table appointment;
drop table appointment_version;

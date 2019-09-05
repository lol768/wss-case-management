# --- !Ups
create table client_consultation (
  id uuid not null,
  university_id varchar(10) not null,
  reason text not null,
  suggested_resolution text not null,
  already_tried text not null,
  session_feedback text not null,
  administrator_outcomes text not null,
  created_utc timestamp(3) not null,
  last_updated_by varchar(100) not null,
  version_utc timestamp(3) not null,
  constraint pk_client_consultation primary key (id)
);

create index idx_client_consultation on client_consultation (university_id);

create table client_consultation_version (
  id uuid not null,
  university_id varchar(10) not null,
  reason text not null,
  suggested_resolution text not null,
  already_tried text not null,
  session_feedback text not null,
  administrator_outcomes text not null,
  created_utc timestamp(3) not null,
  last_updated_by varchar(100) not null,
  version_utc timestamp(3) not null,
  version_operation char(6) not null,
  version_timestamp_utc timestamp(3) not null,
  version_user varchar(100),
  constraint pk_client_consultation_version primary key (id, version_timestamp_utc)
);

create index idx_client_consultation_version on client_consultation_version (id, version_utc);

alter table client_summary drop column initial_consultation;
alter table client_summary_version drop column initial_consultation;

# --- !Downs
drop table client_consultation;
drop table client_consultation_version;

alter table client_summary add column initial_consultation jsonb;
alter table client_summary_version add column initial_consultation jsonb;

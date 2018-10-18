# --- !Ups
create table appointment_team_member (
  usercode varchar(100) not null,
  appointment_id uuid not null,
  created_utc timestamp(3) not null,
  version_utc timestamp(3) not null,
  constraint pk_appointment_team_member primary key (usercode, appointment_id),
  constraint fk_appointment_team_member_appointment foreign key (appointment_id) references appointment (id) on delete restrict,
  constraint fk_appointment_team_member_usercode foreign key (usercode) references member (user_id) on delete restrict
);

create index idx_appointment_team_member_appointment on appointment_team_member (appointment_id);
create index idx_appointment_team_member_usercode on appointment_team_member (usercode);

create table appointment_team_member_version (
  usercode varchar(100) not null,
  appointment_id uuid not null,
  created_utc timestamp(3) not null,
  version_utc timestamp(3) not null,
  version_operation char(6) not null,
  version_timestamp_utc timestamp(3) not null,
  version_user varchar(100),
  constraint pk_appointment_team_member_version primary key (usercode, appointment_id, version_timestamp_utc)
);

create index idx_appointment_team_member_version on appointment_team_member_version (usercode, appointment_id, version_utc);

insert into appointment_team_member (usercode, appointment_id, created_utc, version_utc)
  select team_member, id, current_timestamp, current_timestamp from appointment;

insert into appointment_team_member_version (usercode, appointment_id, created_utc, version_utc, version_operation, version_timestamp_utc)
  select usercode, appointment_id, created_utc, version_utc, 'Insert', current_timestamp from appointment_team_member;

alter table appointment drop column team_member;
alter table appointment_version drop column team_member;

# --- !Downs
alter table appointment add column team_member varchar(100);
alter table appointment_version add column team_member varchar(100);
drop table appointment_team_member_version;
drop table appointment_team_member;
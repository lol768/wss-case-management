# --- !Ups
insert into owner (entity_id, entity_type, user_id, version_utc)
  select id, 'Appointment', team_member, current_timestamp from appointment where team_member is not null;

alter table appointment drop column team_member;
alter table appointment_version drop column team_member;

# --- !Downs
alter table appointment add column team_member varchar(100);
alter table appointment_version add column team_member varchar(100);

# --- !Ups
insert into user_registration_version (university_id, data, version_utc, version_operation, version_timestamp_utc, version_user, last_invited_utc)
select university_id, data, version_utc, 'Delete', current_timestamp, 'system', last_invited_utc from user_registration;
delete from user_registration;

# --- !Downs
insert into user_registration (university_id, data, version_utc, last_invited_utc)
select university_id, data, version_utc, last_invited_utc from user_registration_version where version_operation = 'Delete';
delete from user_registration_version where version_operation = 'Delete';

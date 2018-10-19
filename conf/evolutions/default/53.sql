# --- !Ups
alter table appointment add column appointment_purpose varchar(20);
alter table appointment_version add column appointment_purpose varchar(20);

update appointment set appointment_type = 'Online' where appointment_type = 'Email';
update appointment set appointment_purpose = appointment_type, appointment_type = 'FaceToFace' where appointment_type not in ('FaceToFace', 'Skype', 'Telephone', 'Online');

update appointment set appointment_purpose = '' where appointment_purpose is null;
alter table appointment alter column appointment_purpose set not null;

# --- !Downs
alter table appointment drop column appointment_purpose;
alter table appointment_version drop column appointment_purpose;
update appointment set appointment_type = 'Email' where appointment_type = 'Online';

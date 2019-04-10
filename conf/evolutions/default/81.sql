# --- !Ups
alter table member add column user_id_tsv tsvector;
update member set user_id_tsv = to_tsvector('english', user_id);
alter table member alter column user_id_tsv set not null;
alter table member add column full_name_tsv tsvector;
update member set full_name_tsv = case when full_name is null then ''::tsvector else to_tsvector('english', full_name) end;
alter table member alter column full_name_tsv set not null;

create trigger member_user_id_tsv before insert or update
  on member for each row execute procedure
    tsvector_update_trigger(user_id_tsv, 'pg_catalog.english', user_id);

create trigger member_full_name_tsv before insert or update
  on member for each row execute procedure
    tsvector_update_trigger(full_name_tsv, 'pg_catalog.english', full_name);

create index idx_member_tsv on member using gin(user_id_tsv, full_name_tsv);

alter table client add column university_id_tsv tsvector;
update client set university_id_tsv = to_tsvector('english', university_id);
alter table client alter column university_id_tsv set not null;
alter table client add column full_name_tsv tsvector;
update client set full_name_tsv = case when full_name is null then ''::tsvector else to_tsvector('english', full_name) end;
alter table client alter column full_name_tsv set not null;

create trigger client_university_id_tsv before insert or update
  on client for each row execute procedure
    tsvector_update_trigger(university_id_tsv, 'pg_catalog.english', university_id);

create trigger client_full_name_tsv before insert or update
  on client for each row execute procedure
    tsvector_update_trigger(full_name_tsv, 'pg_catalog.english', full_name);

create index idx_client_tsv on client using gin(university_id_tsv, full_name_tsv);

alter table message add column text_tsv tsvector;
update message set text_tsv = to_tsvector('english', text);
alter table message alter column text_tsv set not null;

create trigger message_text_tsv before insert or update
  on message for each row execute procedure
    tsvector_update_trigger(text_tsv, 'pg_catalog.english', text);

create index idx_message_tsv on message using gin(text_tsv);

alter table appointment add column appointment_key_tsv tsvector;
update appointment set appointment_key_tsv = to_tsvector('english', appointment_key);
alter table appointment alter column appointment_key_tsv set not null;

create trigger appointment_appointment_key_tsv before insert or update
  on appointment for each row execute procedure
    tsvector_update_trigger(appointment_key_tsv, 'pg_catalog.english', appointment_key);

create index idx_appointment_tsv on appointment using gin(appointment_key_tsv);

alter table client_case add column case_key_tsv tsvector;
update client_case set case_key_tsv = to_tsvector('english', case_key);
alter table client_case alter column case_key_tsv set not null;
alter table client_case add column subject_tsv tsvector;
update client_case set subject_tsv = to_tsvector('english', subject);
alter table client_case alter column subject_tsv set not null;

create trigger client_case_case_key_tsv before insert or update
  on client_case for each row execute procedure
    tsvector_update_trigger(case_key_tsv, 'pg_catalog.english', case_key);

create trigger client_case_subject_tsv before insert or update
  on client_case for each row execute procedure
    tsvector_update_trigger(subject_tsv, 'pg_catalog.english', subject);

create index idx_client_case_tsv on client_case using gin(case_key_tsv, subject_tsv);

alter table client_case_note add column text_tsv tsvector;
update client_case_note set text_tsv = to_tsvector('english', text);
alter table client_case_note alter column text_tsv set not null;

create trigger client_case_note_text_tsv before insert or update
  on client_case_note for each row execute procedure
    tsvector_update_trigger(text_tsv, 'pg_catalog.english', text);

create index idx_client_case_note_tsv on client_case_note using gin(text_tsv);

alter table enquiry add column enquiry_key_tsv tsvector;
update enquiry set enquiry_key_tsv = to_tsvector('english', enquiry_key);
alter table enquiry alter column enquiry_key_tsv set not null;
alter table enquiry add column subject_tsv tsvector;
update enquiry set subject_tsv = to_tsvector('english', subject);
alter table enquiry alter column subject_tsv set not null;

create trigger enquiry_enquiry_key_tsv before insert or update
  on enquiry for each row execute procedure
    tsvector_update_trigger(enquiry_key_tsv, 'pg_catalog.english', enquiry_key);

create trigger enquiry_subject_tsv before insert or update
  on enquiry for each row execute procedure
    tsvector_update_trigger(subject_tsv, 'pg_catalog.english', subject);

create index idx_enquiry_tsv on enquiry using gin(enquiry_key_tsv, subject_tsv);

alter table enquiry_note add column text_tsv tsvector;
update enquiry_note set text_tsv = to_tsvector('english', text);
alter table enquiry_note alter column text_tsv set not null;

create trigger enquiry_note_text_tsv before insert or update
  on enquiry_note for each row execute procedure
    tsvector_update_trigger(text_tsv, 'pg_catalog.english', text);

create index idx_enquiry_note_tsv on enquiry_note using gin(text_tsv);

alter table uploaded_file add column file_name_tsv tsvector;
update uploaded_file set file_name_tsv = to_tsvector('english', file_name);
alter table uploaded_file alter column file_name_tsv set not null;

create trigger uploaded_file_file_name_tsv before insert or update
  on uploaded_file for each row execute procedure
    tsvector_update_trigger(file_name_tsv, 'pg_catalog.english', file_name);

create index idx_uploaded_file_tsv on uploaded_file using gin(file_name_tsv);

# --- !Downs
drop index idx_member_tsv;
drop trigger member_full_name_tsv on member;
drop trigger member_user_id_tsv on member;
alter table member drop column full_name_tsv;
alter table member drop column user_id_tsv;

drop index idx_client_tsv;
drop trigger client_full_name_tsv on client;
drop trigger client_university_id_tsv on client;
alter table client drop column full_name_tsv;
alter table client drop column university_id_tsv;

drop index idx_message_tsv;
drop trigger message_text_tsv on message;
alter table message drop column text_tsv;

drop index idx_appointment_tsv;
drop trigger appointment_appointment_key_tsv on appointment;
alter table appointment drop column appointment_key_tsv;

drop index idx_client_case_tsv;
drop trigger client_case_subject_tsv on client_case;
drop trigger client_case_case_key_tsv on client_case;
alter table client_case drop column subject_tsv;
alter table client_case drop column case_key_tsv;

drop index idx_client_case_note_tsv;
drop trigger client_case_note_text_tsv on client_case_note;
alter table client_case_note drop column text_tsv;

drop index idx_enquiry_tsv;
drop trigger enquiry_subject_tsv on enquiry;
drop trigger enquiry_enquiry_key_tsv on enquiry;
alter table enquiry drop column subject_tsv;
alter table enquiry drop column enquiry_key_tsv;

drop index idx_enquiry_note_tsv;
drop trigger enquiry_note_text_tsv on enquiry_note;
alter table enquiry_note drop column text_tsv;

drop index idx_uploaded_file_tsv;
drop trigger uploaded_file_file_name_tsv on uploaded_file;
alter table uploaded_file drop column file_name_tsv;

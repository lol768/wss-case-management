# --- !Ups
alter table client_case_note add column appointment_id uuid;
alter table client_case_note add constraint fk_case_note_appointment foreign key (appointment_id) references appointment (id) on delete restrict;
create index idx_case_note_appointment on client_case_note (appointment_id);

alter table client_case_note_version add column appointment_id uuid;

# --- !Downs
alter table client_case_note drop column appointment_id;
alter table client_case_note_version drop column appointment_id;

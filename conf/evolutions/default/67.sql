# --- !Ups
alter table client_case add medications VARCHAR(100)[] default '{}' not null;
alter table client_case add medication_other text null;
alter table client_case_version add medications VARCHAR(100)[] default '{}' not null;
alter table client_case_version add medication_other text null;

# --- !Downs
alter table client_case drop medications;
alter table client_case_version drop medications;
alter table client_case drop medication_other;
alter table client_case_version drop medication_other;


# --- !Ups
alter table client_case add column duty boolean;
update client_case set duty = false where duty is null;
alter table client_case alter column duty set not null;
alter table client_case_version add column duty boolean;
update client_case_version set duty = false where duty is null;
alter table client_case_version alter column duty set not null;

# --- !Downs
alter table client_case drop column duty;
alter table client_case_version drop column duty;

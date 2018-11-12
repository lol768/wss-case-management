# --- !Ups
alter table client_case add severity_of_problem VARCHAR(100) null;
alter table client_case_version add severity_of_problem VARCHAR(100) null;

# --- !Downs
alter table client_case drop severity_of_problem;
alter table client_case_version drop severity_of_problem;


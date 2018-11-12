# --- !Ups
alter table client_case add counselling_services_issues VARCHAR(100)[] default '{}' not null;
alter table client_case_version add counselling_services_issues VARCHAR(100)[] default '{}' not null;

# --- !Downs
alter table client_case drop counselling_services_issues;
alter table client_case_version drop counselling_services_issues;
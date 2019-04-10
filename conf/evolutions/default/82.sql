# --- !Ups
alter table client_case add mental_health_issues VARCHAR(100)[] default '{}' not null;
alter table client_case_version add mental_health_issues VARCHAR(100)[] default '{}' not null;

# --- !Downs
alter table client_case drop mental_health_issues;
alter table client_case_version drop mental_health_issues;

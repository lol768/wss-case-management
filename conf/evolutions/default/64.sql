# --- !Ups
alter table client_case add client_risk_types VARCHAR(100)[] default '{}' not null;
alter table client_case_version add client_risk_types VARCHAR(100)[] default '{}' not null;

# --- !Downs
alter table client_case drop client_risk_types;
alter table client_case_version drop client_risk_types;

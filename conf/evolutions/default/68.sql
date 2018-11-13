# --- !Ups
ALTER TABLE CLIENT_SUMMARY DROP COLUMN MENTAL_HEALTH_RISK;
ALTER TABLE CLIENT_SUMMARY_VERSION DROP COLUMN MENTAL_HEALTH_RISK;

# --- !Downs
ALTER TABLE CLIENT_SUMMARY ADD COLUMN MENTAL_HEALTH_RISK BOOL NULL;
ALTER TABLE CLIENT_SUMMARY_VERSION ADD COLUMN MENTAL_HEALTH_RISK BOOL NULL;

CREATE INDEX IDX_CLIENT_SUMMARY_MHR ON CLIENT_SUMMARY (MENTAL_HEALTH_RISK);
# --- !Ups
alter table client_case add severity_of_problem VARCHAR(100) null;
alter table client_case_version add severity_of_problem VARCHAR(100) null;

# --- !Downs
alter table client_case drop severity_of_problem;
alter table client_case_version drop severity_of_problem;


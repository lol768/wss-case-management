# --- !Ups
alter table client_case add counselling_services_issues VARCHAR(100)[] default '{}' not null;
alter table client_case_version add counselling_services_issues VARCHAR(100)[] default '{}' not null;

# --- !Downs
alter table client_case drop counselling_services_issues;
alter table client_case_version drop counselling_services_issues;

# --- !Ups
alter table client_case add student_support_issue_types VARCHAR(100)[] default '{}' not null;
alter table client_case add student_support_issue_type_other text null;
alter table client_case_version add student_support_issue_types VARCHAR(100)[] default '{}' not null;
alter table client_case_version add student_support_issue_type_other text null;

# --- !Downs
alter table client_case drop student_support_issue_types;
alter table client_case_version drop student_support_issue_types;
alter table client_case drop student_support_issue_type_other;
alter table client_case_version drop student_support_issue_type_other;


# --- !Ups
alter table APPOINTMENT
  alter outcome type varchar(100)[] using case when outcome is null then '{}' else array[outcome] end,
  alter outcome set default '{}',
  alter outcome set not null;
alter table APPOINTMENT_VERSION
  alter outcome type varchar(100)[] using case when outcome is null then '{}' else array[outcome] end,
  alter outcome set default '{}',
  alter outcome set not null;

# --- !Downs
alter table APPOINTMENT
  alter outcome drop not null,
  alter outcome drop default;

update appointment set outcome = null;
alter table appointment alter outcome type varchar(100);

alter table APPOINTMENT_VERSION
  alter outcome drop not null,
  alter outcome drop default;

update appointment_version set outcome = null;
alter table appointment_version alter outcome type varchar(100);

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


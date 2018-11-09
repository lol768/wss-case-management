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

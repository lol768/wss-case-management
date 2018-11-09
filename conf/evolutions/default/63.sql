# --- !Ups
alter table APPOINTMENT
  alter outcome type varchar(100)[] using array[outcome],
  alter outcome set default '{}',
  alter outcome set not null;
alter table APPOINTMENT_VERSION
  alter outcome type varchar(100)[] using array[outcome],
  alter outcome set default '{}',
  alter outcome set not null;

-- junk existing data
update appointment set outcome = '{}';
update appointment_version set outcome = '{}';

# --- !Downs
alter table APPOINTMENT
  alter outcome drop not null,
  alter outcome drop default,
  alter outcome type varchar(100);
alter table APPOINTMENT_VERSION
  alter outcome drop not null,
  alter outcome drop default,
  alter outcome type varchar(100);

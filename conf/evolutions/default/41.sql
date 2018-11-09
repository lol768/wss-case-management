# --- !Ups
alter table appointment add column state VARCHAR(20) NOT NULL DEFAULT 'Provisional';
alter table appointment_version add column state VARCHAR(20) NOT NULL DEFAULT 'Provisional';
alter table appointment alter column state drop default;
alter table appointment_version alter column state drop default;

create index idx_appointment_state on appointment (state);

# --- !Downs
alter table appointment drop column state;
alter table appointment_version drop column state;


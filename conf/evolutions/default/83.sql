# --- !Ups
alter table client_summary add reasonable_adjustments_notes text default '' not null;
alter table client_summary_version add reasonable_adjustments_notes text default '' not null;

# --- !Downs
alter table client_summary drop reasonable_adjustments_notes;
alter table client_summary_version drop reasonable_adjustments_notes;

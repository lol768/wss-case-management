# --- !Ups
update client_summary set notes = '' where notes is null;
alter table client_summary alter column notes set not null;
update client_summary_version set notes = '' where notes is null;
alter table client_summary_version alter column notes set not null;

update client_summary set alt_contact_number = '' where alt_contact_number is null;
alter table client_summary alter column alt_contact_number set not null;
update client_summary_version set alt_contact_number = '' where alt_contact_number is null;
alter table client_summary_version alter column alt_contact_number set not null;

update client_summary set alt_email = '' where alt_email is null;
alter table client_summary alter column alt_email set not null;
update client_summary_version set alt_email = '' where alt_email is null;
alter table client_summary_version alter column alt_email set not null;

# --- !Downs


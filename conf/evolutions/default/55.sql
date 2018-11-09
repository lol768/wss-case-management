# --- !Ups
alter table owner add outlook_id VARCHAR(200);
alter table owner_version add outlook_id VARCHAR(200);

# --- !Downs
alter table owner drop column outlook_id;
alter table owner_version drop column outlook_id;
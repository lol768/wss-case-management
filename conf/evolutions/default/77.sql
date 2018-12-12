# --- !Ups
alter table location_room add column office365_usercode varchar(100);
alter table location_room_version add column office365_usercode varchar(100);

# --- !Downs
alter table location_room drop column office365_usercode;
alter table location_room_version drop column office365_usercode;

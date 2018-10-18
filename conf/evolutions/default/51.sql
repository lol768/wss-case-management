# --- !Ups
create table location_building (
  id uuid not null,
  name varchar(200) not null,
  wai2go_id integer not null,
  created_utc timestamp(3) not null,
  version_utc timestamp(3) not null,
  constraint pk_location_building primary key (id)
);

create table location_building_version (
  id uuid not null,
  name varchar(200) not null,
  wai2go_id integer not null,
  created_utc timestamp(3) not null,
  version_utc timestamp(3) not null,
  version_operation char(6) not null,
  version_timestamp_utc timestamp(3) not null,
  version_user varchar(100),
  constraint pk_location_building_version primary key (id, version_timestamp_utc)
);

create index idx_location_building_version on location_building_version (id, version_utc);

create table location_room (
  id uuid not null,
  building_id uuid not null,
  name varchar(200) not null,
  wai2go_id integer,
  is_available boolean not null,
  created_utc timestamp(3) not null,
  version_utc timestamp(3) not null,
  constraint pk_location_room primary key (id),
  constraint fk_location_room_building foreign key (building_id) references location_building (id) on delete restrict
);

create index idx_location_room_building on location_room (building_id);

create table location_room_version (
  id uuid not null,
  building_id uuid not null,
  name varchar(200) not null,
  wai2go_id integer,
  is_available boolean not null,
  created_utc timestamp(3) not null,
  version_utc timestamp(3) not null,
  version_operation char(6) not null,
  version_timestamp_utc timestamp(3) not null,
  version_user varchar(100),
  constraint pk_location_room_version primary key (id, version_timestamp_utc)
);

create index idx_location_room_version on location_room_version (id, version_utc);

alter table appointment add column room_id uuid;
alter table appointment_version add column room_id uuid;

alter table appointment add constraint fk_appointment_room foreign key (room_id) references location_room (id) on delete restrict;
create index idx_appointment_room on appointment (room_id);

alter table appointment drop column location;
alter table appointment_version drop column location;

-- Pre-populate rooms CASE-248
insert into location_building (id, name, wai2go_id, created_utc, version_utc)
  values ('b80302b5-39e7-47dd-8c32-e5e9b997ffde'::uuid, 'Ramphal', 41355, current_timestamp, current_timestamp);
insert into location_building (id, name, wai2go_id, created_utc, version_utc)
  values ('affa1859-9941-4267-a272-b416593970d6'::uuid, 'Senate House', 24046, current_timestamp, current_timestamp);
insert into location_building (id, name, wai2go_id, created_utc, version_utc)
  values ('6e3eb8f2-578a-4f83-bca6-d7355a71b7c3'::uuid, 'Social Sciences', 47971, current_timestamp, current_timestamp);
insert into location_building (id, name, wai2go_id, created_utc, version_utc)
  values ('bb32e42d-f593-45db-abff-efbdaf70eed1'::uuid, 'University House', 50306, current_timestamp, current_timestamp);
insert into location_building (id, name, wai2go_id, created_utc, version_utc)
  values ('d79b7566-a3f6-4453-8e37-1bf8022bf009'::uuid, 'Westwood House', 23790, current_timestamp, current_timestamp);

insert into location_building_version (id, name, wai2go_id, created_utc, version_utc, version_operation, version_timestamp_utc)
  select id, name, wai2go_id, created_utc, version_utc, 'Insert', current_timestamp from location_building;

insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('99c91ec6-3c25-45ab-bd5a-7db214ef8439'::uuid, 'b80302b5-39e7-47dd-8c32-e5e9b997ffde'::uuid, 'R2.09', 27738, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('8ce472bc-9218-49dd-9b16-974219741737'::uuid, 'b80302b5-39e7-47dd-8c32-e5e9b997ffde'::uuid, 'R2.10', 27750, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('3a775300-e80f-425d-98b1-02c15b4b75d1'::uuid, 'b80302b5-39e7-47dd-8c32-e5e9b997ffde'::uuid, 'R2.11', 27739, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('f8d3b21d-87fc-4928-9648-907f88a86867'::uuid, 'b80302b5-39e7-47dd-8c32-e5e9b997ffde'::uuid, 'R2.12', 27736, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('a16da94c-bbec-43cf-a58b-17960593881d'::uuid, 'b80302b5-39e7-47dd-8c32-e5e9b997ffde'::uuid, 'R2.13', 27740, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('ad111d2d-a0a8-49d2-9ae5-28d3245a8bcd'::uuid, 'b80302b5-39e7-47dd-8c32-e5e9b997ffde'::uuid, 'R2.14', 27735, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('040607e0-306f-4f81-9eb6-205b11d22557'::uuid, 'b80302b5-39e7-47dd-8c32-e5e9b997ffde'::uuid, 'R2.15', 27741, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('5caf7187-3009-4990-b8d5-332663d73798'::uuid, 'b80302b5-39e7-47dd-8c32-e5e9b997ffde'::uuid, 'R2.16', 27734, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('b0e06c8a-f913-4457-9f67-8c5d43b7b4af'::uuid, 'b80302b5-39e7-47dd-8c32-e5e9b997ffde'::uuid, 'R2.17', 27751, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('b686277e-8b82-4a10-81cc-11bde1a3efbe'::uuid, 'b80302b5-39e7-47dd-8c32-e5e9b997ffde'::uuid, 'R3.11', 27773, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('4f2b9c56-9c80-4499-b499-b60586030878'::uuid, 'b80302b5-39e7-47dd-8c32-e5e9b997ffde'::uuid, 'R3.13', 27774, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('b038d680-d4df-43c1-ac0d-1fde12b7f72b'::uuid, 'b80302b5-39e7-47dd-8c32-e5e9b997ffde'::uuid, 'R3.15', 27775, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('0b3a0c72-e170-431f-ba44-3f58e7c57b6e'::uuid, 'b80302b5-39e7-47dd-8c32-e5e9b997ffde'::uuid, 'R3.16', 27770, true, current_timestamp, current_timestamp);

insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('98944a13-9cf1-48cc-840e-4d5781a092fc'::uuid, 'affa1859-9941-4267-a272-b416593970d6'::uuid, 'RL Hub 1', 26047, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('f8c5c5d3-1851-4e36-8c36-0d638e374266'::uuid, 'affa1859-9941-4267-a272-b416593970d6'::uuid, 'RL Hub 2', 26047, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('3ede64b0-10d4-4e69-bd68-58a5f36a3760'::uuid, 'affa1859-9941-4267-a272-b416593970d6'::uuid, 'RL Hub 3', 26047, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('56ce3e5c-d9ba-4538-b867-f461c0b66d3f'::uuid, 'affa1859-9941-4267-a272-b416593970d6'::uuid, 'RL Hub 4', 26047, true, current_timestamp, current_timestamp);

insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('154a9df5-14d2-4552-85aa-87a72d6a2b2b'::uuid, '6e3eb8f2-578a-4f83-bca6-d7355a71b7c3'::uuid, 'S1.98', 37961, true, current_timestamp, current_timestamp);

insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('74343880-d3d6-4a3a-91bd-d8b3d8acd5a6'::uuid, 'bb32e42d-f593-45db-abff-efbdaf70eed1'::uuid, 'WSS room 1', null, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('96c5dc0a-11f4-430a-b3fb-3fde0b515bf0'::uuid, 'bb32e42d-f593-45db-abff-efbdaf70eed1'::uuid, 'WSS room 2', null, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('e1688d52-7dc2-450c-b7b6-c9ef8a7d2952'::uuid, 'bb32e42d-f593-45db-abff-efbdaf70eed1'::uuid, 'WSS room 3', null, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('15bc41bd-4025-4531-a607-8883aeeeaf7a'::uuid, 'bb32e42d-f593-45db-abff-efbdaf70eed1'::uuid, 'WSS room 4', null, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('7de346c3-29ae-4c3e-97c7-1669c5b87d0e'::uuid, 'bb32e42d-f593-45db-abff-efbdaf70eed1'::uuid, 'WSS room 5', null, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('7cb94c62-ac6c-4a03-bc7f-8732f19834f8'::uuid, 'bb32e42d-f593-45db-abff-efbdaf70eed1'::uuid, 'WSS room 6', null, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('2aec3d28-faec-41ac-89af-cc7e7adbbc39'::uuid, 'bb32e42d-f593-45db-abff-efbdaf70eed1'::uuid, 'WSS room 7', null, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('688f0d50-2950-481e-b73b-e7a159f0c43a'::uuid, 'bb32e42d-f593-45db-abff-efbdaf70eed1'::uuid, 'WSS room 8', null, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('62b218d4-2ec7-4ae4-83f1-7e2057d01901'::uuid, 'bb32e42d-f593-45db-abff-efbdaf70eed1'::uuid, 'WSS room 9', null, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('c1b0e188-cc18-4f1d-908f-16eb71347f75'::uuid, 'bb32e42d-f593-45db-abff-efbdaf70eed1'::uuid, 'WSS room 10', null, true, current_timestamp, current_timestamp);

insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('7e87f10e-7fd0-411e-b95a-cedec0ce4cb2'::uuid, 'd79b7566-a3f6-4453-8e37-1bf8022bf009'::uuid, 'W001', 35293, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('d63824a7-3c1d-462a-bb8f-2145130d2a39'::uuid, 'd79b7566-a3f6-4453-8e37-1bf8022bf009'::uuid, 'W002', null, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('76ac7fe5-2199-4eb5-a386-27e65c53c293'::uuid, 'd79b7566-a3f6-4453-8e37-1bf8022bf009'::uuid, 'W005', null, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('d88e1550-0ba4-49ab-bc75-a6ac2c3e4954'::uuid, 'd79b7566-a3f6-4453-8e37-1bf8022bf009'::uuid, 'W006b', 35296, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('3a56cc7b-1cdc-4c5c-b59e-7ee805e39cd9'::uuid, 'd79b7566-a3f6-4453-8e37-1bf8022bf009'::uuid, 'W006a', 35281, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('db6f70e4-c3cf-4870-8652-6bc19664b9b5'::uuid, 'd79b7566-a3f6-4453-8e37-1bf8022bf009'::uuid, 'W013', null, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('8b156a5a-b703-4b71-bacf-2c830248cedc'::uuid, 'd79b7566-a3f6-4453-8e37-1bf8022bf009'::uuid, 'W014b', 52250, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('e6731a15-6a52-4db2-b69b-0748360d8976'::uuid, 'd79b7566-a3f6-4453-8e37-1bf8022bf009'::uuid, 'W014c', 52251, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('7347c366-1abb-4f18-932a-eaf5362235d4'::uuid, 'd79b7566-a3f6-4453-8e37-1bf8022bf009'::uuid, 'W015', 35304, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('8ed2ee14-9b4d-42a9-9a0a-4539cd3cc61e'::uuid, 'd79b7566-a3f6-4453-8e37-1bf8022bf009'::uuid, 'W016', 35288, true, current_timestamp, current_timestamp);
insert into location_room (id, building_id, name, wai2go_id, is_available, created_utc, version_utc)
  values ('44e03a46-1434-41c9-bf4a-01a10b442b76'::uuid, 'd79b7566-a3f6-4453-8e37-1bf8022bf009'::uuid, 'W017', 35289, true, current_timestamp, current_timestamp);

insert into location_room_version (id, building_id, name, wai2go_id, is_available, created_utc, version_utc, version_operation, version_timestamp_utc)
  select id, building_id, name, wai2go_id, is_available, created_utc, version_utc, 'Insert', current_timestamp from location_room;

# --- !Downs
alter table appointment add column location VARCHAR(200);
alter table appointment_version add column location VARCHAR(200);
alter table appointment drop column room_id;
alter table appointment_version drop column room_id;
drop table location_room_version;
drop table location_room;
drop table location_building_version;
drop table location_building;
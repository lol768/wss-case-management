# --- !Ups
create table user_preferences (
  user_id varchar(255) not null,
  preferences text not null,
  version_utc timestamp(3) not null,
  constraint pk_user_preferences primary key (user_id)
);

create table user_preferences_version (
  user_id varchar(255) not null,
  preferences text not null,
  version_utc timestamp(3) not null,
  version_operation char(6) not null,
  version_timestamp_utc timestamp(3) not null,
  version_user varchar(100),
  constraint pk_user_preferences_version primary key (user_id, version_timestamp_utc)
);

create index idx_user_preferences_version on user_preferences_version (user_id, version_utc);

# --- !Downs
drop table user_preferences;
drop table user_preferences_version;
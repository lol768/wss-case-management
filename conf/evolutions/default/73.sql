# --- !Ups
alter table appointment add dsa_action_points VARCHAR(100)[] default '{}' not null;
alter table appointment add dsa_action_point_other text null;
alter table appointment_version add dsa_action_points VARCHAR(100)[] default '{}' not null;
alter table appointment_version add dsa_action_point_other text null;

# --- !Downs
alter table appointment drop dsa_action_points;
alter table appointment_version drop dsa_action_points;
alter table appointment drop dsa_action_point_other;
alter table appointment_version drop dsa_action_point_other;

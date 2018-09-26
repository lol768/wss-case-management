# --- !Ups

update appointment set team_id = 'wellbeing' where team_id = 'studentsupport';
update appointment_version set team_id = 'wellbeing' where team_id = 'studentsupport';
update client_case set team_id = 'wellbeing' where team_id = 'studentsupport';
update client_case_version set team_id = 'wellbeing' where team_id = 'studentsupport';
update enquiry set team_id = 'wellbeing' where team_id = 'studentsupport';
update enquiry_version set team_id = 'wellbeing' where team_id = 'studentsupport';
update message set team_id = 'wellbeing' where team_id = 'studentsupport';
update message_version set team_id = 'wellbeing' where team_id = 'studentsupport';

# --- !Downs

update appointment set team_id = 'studentsupport' where team_id = 'wellbeing';
update appointment_version set team_id = 'studentsupport' where team_id = 'wellbeing';
update client_case set team_id = 'studentsupport' where team_id = 'wellbeing';
update client_case_version set team_id = 'studentsupport' where team_id = 'wellbeing';
update enquiry set team_id = 'studentsupport' where team_id = 'wellbeing';
update enquiry_version set team_id = 'studentsupport' where team_id = 'wellbeing';
update message set team_id = 'studentsupport' where team_id = 'wellbeing';
update message_version set team_id = 'studentsupport' where team_id = 'wellbeing';

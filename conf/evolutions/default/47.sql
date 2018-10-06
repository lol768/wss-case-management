# --- !Ups

UPDATE appointment SET state = 'Accepted' WHERE state = 'Confirmed';
UPDATE appointment_version SET state = 'Accepted' WHERE state = 'Confirmed';
UPDATE appointment_client SET state = 'Accepted' WHERE state = 'Confirmed';
UPDATE appointment_client_version SET state = 'Accepted' WHERE state = 'Confirmed';

# --- !Downs

UPDATE appointment SET state = 'Confirmed' WHERE state = 'Accepted';
UPDATE appointment_version SET state = 'Confirmed' WHERE state = 'Accepted';
UPDATE appointment_client SET state = 'Confirmed' WHERE state = 'Accepted';
UPDATE appointment_client_version SET state = 'Confirmed' WHERE state = 'Accepted';
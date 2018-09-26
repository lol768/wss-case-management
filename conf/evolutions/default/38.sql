# --- !Ups

ALTER TABLE reasonable_adjustment ALTER COLUMN reasonable_adjustment SET DATA TYPE VARCHAR(30);

ALTER TABLE reasonable_adjustment_version ALTER COLUMN reasonable_adjustment SET DATA TYPE VARCHAR(30);

# --- !Downs

ALTER TABLE reasonable_adjustment ALTER COLUMN reasonable_adjustment SET DATA TYPE VARCHAR(20);

ALTER TABLE reasonable_adjustment_version ALTER COLUMN reasonable_adjustment SET DATA TYPE VARCHAR(20);



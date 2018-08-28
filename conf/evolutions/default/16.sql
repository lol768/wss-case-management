# --- !Ups
ALTER TABLE CLIENT_SUMMARY ADD COLUMN MENTAL_HEALTH_RISK BOOL NULL;
ALTER TABLE CLIENT_SUMMARY_VERSION ADD COLUMN MENTAL_HEALTH_RISK BOOL NULL;

CREATE INDEX IDX_CLIENT_SUMMARY_MHR ON CLIENT_SUMMARY (MENTAL_HEALTH_RISK);

# --- !Downs
ALTER TABLE CLIENT_SUMMARY DROP COLUMN MENTAL_HEALTH_RISK;
ALTER TABLE CLIENT_SUMMARY_VERSION DROP COLUMN MENTAL_HEALTH_RISK;
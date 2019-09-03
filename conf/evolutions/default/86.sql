# --- !Ups
update client_summary
set initial_consultation = initial_consultation || '{"sessionFeedback":"", "administratorOutcomes":""}'::jsonb
where initial_consultation is not null;
update client_summary_version
set initial_consultation = initial_consultation || '{"sessionFeedback":"", "administratorOutcomes":""}'::jsonb
where initial_consultation is not null;

# --- !Downs
update client_summary
set initial_consultation = (initial_consultation - 'sessionFeedback') - 'administratorOutcomes'
where initial_consultation is not null;
update client_summary_version
set initial_consultation = (initial_consultation - 'sessionFeedback') - 'administratorOutcomes'
where initial_consultation is not null;

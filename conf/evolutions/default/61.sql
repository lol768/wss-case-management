# --- !Ups
update client_case_link set link_type = 'Related' where link_type = 'Merge';

# --- !Downs

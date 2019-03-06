# --- !Ups

alter table dsa_application add column customer_reference varchar(1000);
alter table dsa_application_version add column customer_reference varchar(1000);

# --- !Downs

alter table dsa_application drop column customer_reference;
alter table dsa_application_version drop column customer_reference;

# --- !Ups
update client_case_document set document_type = 'WellbeingSupportInformationForm' where document_type = 'StudentSupportInformationForm';
update client_case_document set document_type = 'Other' where document_type in ('DisabilityNeedsAssessmentReport', 'SelectedEmails', 'StudentSupportInternalDocuments');

# --- !Downs
update client_case_document set document_type = 'StudentSupportInformationForm' where document_type = 'WellbeingSupportInformationForm';
update client_case_document set document_type = 'SelectedEmails' where document_type = 'Other';
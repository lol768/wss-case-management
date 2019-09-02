# --- !Ups
create table message_snippet (
  id uuid not null,
  title text not null,
  body text not null,
  sort_order integer not null,
  version_utc timestamp(3) not null,
  constraint pk_message_snippet primary key (id)
);

create table message_snippet_version (
  id uuid not null,
  title text not null,
  body text not null,
  sort_order integer not null,
  version_utc timestamp(3) not null,
  version_operation char(6) not null,
  version_timestamp_utc timestamp(3) not null,
  version_user varchar(100),
  constraint pk_message_snippet_version primary key (id, version_timestamp_utc)
);

create index idx_message_snippet_version on message_snippet_version (id, version_utc);

-- Seed data
insert into message_snippet (id, title, body, sort_order, version_utc)
  values (
    '2c5912d6-6bb3-4599-a891-d77bb48c1e0e'::uuid,
    'Wellbeing Support - standard response',
    'Thank you for coming along to a brief consultation session in Wellbeing Support Services today.  We hope you found it helpful.

We discussed what might be the best next step for you, and this is outlined below. Please keep in touch if there is anything more we can help you with.

',
    0,
    current_timestamp
  );
insert into message_snippet (id, title, body, sort_order, version_utc)
  values (
    '051226ed-4734-4874-b9e3-6dd10021260f'::uuid,
    'Exam Stress link',
    '[Exam Stress](https://warwick.ac.uk/services/studentsupport/wellbeing/examstress/)',
    0,
    current_timestamp
  );
insert into message_snippet (id, title, body, sort_order, version_utc)
  values (
    'c81bec9b-27ad-454d-9a2a-62b2f6dbe50d'::uuid,
    'Procrastination link',
    '[Procrastination](https://warwick.ac.uk/services/studentsupport/wellbeing/procrastination/)',
    0,
    current_timestamp
  );
insert into message_snippet (id, title, body, sort_order, version_utc)
  values (
    'ea5f4cfc-81b3-4773-8d16-776967046147'::uuid,
    'Sleep link',
    '[Sleep](https://warwick.ac.uk/services/studentsupport/wellbeing/sleep/)',
    0,
    current_timestamp
  );
insert into message_snippet (id, title, body, sort_order, version_utc)
  values (
    '0d84f992-f731-4769-a25e-12e28fdcadc6'::uuid,
    'Anxiety link',
    '[Anxiety](https://warwick.ac.uk/services/studentsupport/wellbeing/anxiety/)',
    0,
    current_timestamp
  );
insert into message_snippet (id, title, body, sort_order, version_utc)
  values (
    'f80a0c07-2de5-40d4-b81d-164faf97148f'::uuid,
    'Stress link',
    '[Stress](https://warwick.ac.uk/services/studentsupport/wellbeing/stress/)',
    0,
    current_timestamp
  );
insert into message_snippet (id, title, body, sort_order, version_utc)
  values (
    'a2233e9c-2124-4eb9-a8d1-42c6d0edeb9d'::uuid,
    'Work-Life Balance link',
    '[Work-Life Balance](https://warwick.ac.uk/services/studentsupport/wellbeing/worklife/)',
    0,
    current_timestamp
  );

insert into message_snippet (id, title, body, sort_order, version_utc)
  values (
    '236400e5-35c8-4fde-b028-9f893b84ca79'::uuid,
    'Disability - evidence template',
    'Thanks for getting in touch about accommodation. Disability Services can help support students who have specific requirements for their accommodation such as a level access room or an en-suite. In order to support your request you need to provide medical evidence that’s written by your GP or other suitable medical professional, that states your diagnosis, the impact it has on a day to day basis, stating that SPECIFIC REQUIREMENT is a need due to a disability and typed on headed paper.

When you have the letter you can scan it over to me and I can contact the accommodation team.

__Please note we cannot support students for a specific hall of residence__

I hope that helps and if you have any questions, please let me know.

All the best,
',
    0,
    current_timestamp
  );
insert into message_snippet (id, title, body, sort_order, version_utc)
  values (
    '6be9429e-05cc-4e43-a4ac-1d9c30de6adc'::uuid,
    'Disability - not engaged',
    'Disability Services offer advice and support to students with disabilities at the University. We encourage students who have not made contact with us yet and students who are not accessing any recommended support to engage with us as soon as possible to discuss their support needs. All support arrangements including special exam arrangements on disability and mental health grounds are evidence based and informed by supporting documentation.',
    0,
    current_timestamp
  );
insert into message_snippet (id, title, body, sort_order, version_utc)
  values (
    '38da6bff-f743-41ae-b7aa-7c3b74b93166'::uuid,
    'Disability - disclosed via UCAS',
    'We are emailing you because you have disclosed a disability on your application form to the University. Disability Services in Wellbeing Support Services offer advice, guidance and support to students with disabilities for the duration of their studies. You can find information about our services at https://warwick.ac.uk/services/disability

Students are encouraged to register with the service to discuss their individual support requirements and make appropriate arrangements to access support. Support arrangements, including special exam arrangements, are evidence based, are not automatic and they will not be implemented without student engagement. Evidence of your disability can be emailed to [disability@warwick.ac.uk](mailto:disability@warwick.ac.uk?Subject=Evidence%20of%20disability) or provided on arrival.

Continuing students who have not yet registered with the service or have not engaged with the support offered are advised to make contact with us to review their support needs. New students are advised to register with the service as soon as possible to formalise support arrangements.

You can find information about other support services at the University at https://warwick.ac.uk/services/supportservices

Best wishes,

Disability Services',
    0,
    current_timestamp
  );
-- the attachment for this will still need adding manually
insert into message_snippet (id, title, body, sort_order, version_utc)
  values (
    'a2a49ca0-903f-44d4-9c84-44255f9f7b02'::uuid,
    'Disability - evacuation requirement template/form',
    'Dear all,

Following previous correspondence via Disability Services regarding assessing your personal evacuation requirements, we would ask that you complete the Independence Assessment Form at the following link and return to us by the ….  The purpose of the form and guidance is to give you an understanding and assurance of your ability to evacuate in the event of an incident, such as a fire alarm, and any potential assistance you might require in the case of an emergency evacuation.

You can find the Independence Assessment Tool, general Guidance, and specific guidance at https://warwick.ac.uk/services/healthsafetywellbeing/guidance/fire/assistedevacuation/

You can return the completed form at [disability@warwick.ac.uk](mailto:disability@warwick.ac.uk)

Also attached is some additional information about the University’s refuge areas.

Best wishes,

Disability Services',
    0,
    current_timestamp
  );
insert into message_snippet (id, title, body, sort_order, version_utc)
  values (
    'f1c71632-1df5-45b0-b644-bf14c93e6041'::uuid,
    'Disability - exam access arrangements reminder',
    'Dear Student,

Disability Services at the University provide confidential advice, support and guidance to students with disabilities and Specific Learning Differences such as dyslexia.

To enable us to meet your individual needs, we would advise that you register with us as soon as possible to discuss your support requirements.

You will need to provide your medical/diagnostic evidence in support of your requirements. If we have not already received this, you can email your evidence to [disability@warwick.ac.uk](mailto:disability@warwick.ac.uk?Subject=Evidence%20of%20disability) or bring a copy with you.

We would also like to bring to your attention the deadline dates for special examination arrangements for this academic year:

Further details about special exam arrangements can be found here:

https://warwick.ac.uk/services/disability/howwecanhelp/examinations/

If you will be sitting exams in the December to March period, please make contact with Disability Services, **as soon as possible.**

Please note that support arrangements, including examination entitlements are **NOT** automatic and can only be put in place **after we meet** with you to discuss your individual requirements.

If you have already met with Disability Services and have access arrangements in place then you **do not need to do anything further. Your previous exam arrangements will stay in place unless your circumstances change and you contact us.**

Best wishes,

Disability Services',
    0,
    current_timestamp
  );
insert into message_snippet (id, title, body, sort_order, version_utc)
  values (
    'ac0c111d-c163-4db9-a365-d125eb23e121'::uuid,
    'Disability - appointment',
    'Dear ,

Thank you for contacting Disability Services. We can offer you an appointment on ……..at ….. with….. Please confirm whether you are able to attend this appointment.

We are based in the Wellbeing Support Services area on the ground floor of Senate House. Please check in at the Wellbeing Support Services reception when you arrive.

We ask that you bring a copy of your medical/diagnostic evidence to the appointment.  If you do not provide supporting evidence it will be very difficult to advise you on the most relevant support that would meet your needs.

Best wishes,

Disability Services',
    0,
    current_timestamp
  );
insert into message_snippet (id, title, body, sort_order, version_utc)
  values (
    '16d3e295-58d7-4b3e-a34f-c510f0627ed0'::uuid,
    'Telephone call – A brief consultation',
    'You have contacted Wellbeing Support Services and asked for a brief consultation by telephone.  We will send you an appointment slot and ask you to accept this if it is convenient to you, or contact us if you wish to re-schedule.  Telephone call consultations are usually brief and we allocate a longer slot, simply to give you a guide as to between which times you can expect our call.  We do our best to contact you between these times.',
    0,
    current_timestamp
  );

# --- !Downs
drop table message_snippet;
drop table message_snippet_version;

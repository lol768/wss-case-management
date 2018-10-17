/* eslint-env browser */
import $ from 'jquery';
import 'fullcalendar';
import 'fullcalendar-scheduler';
import log from 'loglevel';
import moment from 'moment-timezone';
import { addQsToUrl, postJsonWithCredentials } from './serverpipe';
import { formatDateMoment, formatTimeMoment } from './dateFormats';

export default function AppointmentFreeBusyForm(form) {
  const $form = $(form);

  const getFormValues = name => $form.find(`:input[name="${name}"]`)
    .filter((i, el) => !!el.value)
    .map((i, el) => el.value)
    .get();

  $form.on('init.datetimepicker', (ev) => {
    const $div = $(ev.target);
    const dateTimePicker = $div.data('DateTimePicker');

    $div.find('.timepicker').append(
      $('<button />')
        .addClass('btn btn-default')
        .attr('type', 'button')
        .text('Free / busy check')
        .on('click', (e) => {
          e.preventDefault();
          e.stopPropagation();

          // Get clients
          const clients = getFormValues('clients[]');
          const teamMembers = getFormValues('appointment.teamMember');
          const rooms = getFormValues('appointment.roomID');

          if ([...clients, ...teamMembers, ...rooms].length) {
            $('#appointment-freebusy-modal').modal('show');
          }
        }),
    );

    $('#appointment-freebusy-modal')
      .on('shown.bs.modal', (e) => {
        const $modal = $(e.target);
        const currentHint = $modal.find('.modal-body .current');

        const updateSelected = (start, durationMinutes) => {
          currentHint.text(`${formatTimeMoment(start)} - ${formatTimeMoment(start.add(durationMinutes, 'm'))} (${durationMinutes} minutes), ${formatDateMoment(start)}`);
        };
        updateSelected(dateTimePicker.date(), ($('#appointment_duration').val() || 0) / 60);

        // Get clients
        const clients = getFormValues('clients[]');
        const teamMembers = getFormValues('appointment.teamMember');
        const roomIDs = getFormValues('appointment.roomID');

        $modal.find('.appointment-freebusy-calendar').fullCalendar({
          schedulerLicenseKey: 'CC-Attribution-NonCommercial-NoDerivatives',
          themeSystem: 'bootstrap3',
          bootstrapGlyphicons: {
            close: ' fal fa-times',
            prev: ' fal fa-chevron-left',
            next: ' fal fa-chevron-right',
            prevYear: ' fal fa-backward',
            nextYear: ' fal fa-forward',
          },
          header: {
            left: 'prev,next today title',
            center: '',
            right: '',
          },
          defaultView: 'agendaDay',
          defaultDate: dateTimePicker.date(),
          groupByResource: true,
          agendaDay: {
            titleFormat: 'dddd, MMM D, YYYY',
            columnFormat: 'dddd D/MM',
          },
          firstDay: 1,
          allDaySlot: false,
          slotEventOverlap: true,
          slotLabelFormat: 'HH:mm',
          slotDuration: '00:15:00',
          slotLabelInterval: '01:00:00',
          timeFormat: 'HH:mm',
          minTime: '08:00:00',
          maxTime: '19:00:00',
          weekends: false,
          nowIndicator: true,
          timezone: 'Europe/London',
          events: (start, end, timezone, callback) => {
            postJsonWithCredentials(
              addQsToUrl($form.data('freebusy'), {
                start: start.utc().toISOString(),
                end: end.utc().toISOString(),
                timezone,
              }),
              {
                clients,
                teamMembers,
                roomIDs,
              },
            )
              .then(response => response.json())
              .catch((err) => {
                log.error(err);
                callback([]);
              })
              .then((response) => {
                if (response.success) {
                  callback(response.data);
                } else {
                  log.error(response.errors);
                  callback([]);
                }
              });
          },
          resources: (callback) => {
            postJsonWithCredentials(
              $form.data('freebusyresources'),
              {
                clients,
                teamMembers,
                roomIDs,
              },
            )
              .then(response => response.json())
              .catch((err) => {
                log.error(err);
                callback([]);
              })
              .then((response) => {
                if (response.success) {
                  callback(response.data);
                } else {
                  log.error(response.errors);
                  callback([]);
                }
              });
          },
          resourceRender: (resource, $cell) => {
            if (resource.type) {
              $cell.append($('<br />')).append($('<span />').addClass('hint').text(`(${resource.type})`));
            }
          },
          selectable: true,
          selectHelper: true,
          select: (start, end) => {
            dateTimePicker.date(start);
            $('#appointment_duration').val(moment.duration(end.diff(start)).asSeconds());

            updateSelected(start, moment.duration(end.diff(start)).asMinutes());
          },
          selectAllow: ({ start, end }) => {
            // Return false if we're selecting an invalid duration
            const duration = moment.duration(end.diff(start)).asSeconds();
            return !!$(`#appointment_duration > option[value="${duration}"]`).length;
          },
        });
      })
      .on('hidden.bs.modal', (e) => {
        const $modal = $(e.target);

        $modal.find('.appointment-freebusy-calendar').fullCalendar('destroy');
      });
  });
}

/* eslint-env browser */
import $ from 'jquery';
import 'fullcalendar';
import 'fullcalendar-scheduler';
import log from 'loglevel';
import moment from 'moment-timezone';
import { addQsToUrl, postJsonWithCredentials } from './serverpipe';
import { formatDateMoment, formatTimeMoment } from './dateFormats';
import CommonFullCalendarOptions from './common-fullcalendar-options';

export default function AppointmentFreeBusyForm(form) {
  const $form = $(form);

  const getFormValues = name => $form.find(`:input[name="${name}"]`)
    .filter((i, el) => !!el.value)
    .map((i, el) => el.value)
    .get();

  $form.on('input change', () => {
    const clients = getFormValues('clients[]');
    const teamMembers = getFormValues('teamMembers[]');
    const rooms = getFormValues('appointment.roomID');

    const $button = $form.find('.timepicker button');
    $button.prop('disabled', ![...clients, ...teamMembers, ...rooms].length);
  });

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

          const clients = getFormValues('clients[]');
          const teamMembers = getFormValues('teamMembers[]');
          const rooms = getFormValues('appointment.roomID');

          if ([...clients, ...teamMembers, ...rooms].length) {
            $('#appointment-freebusy-modal').modal('show');
          }
        }),
    );

    $('#appointment-freebusy-modal')
      .on('shown.bs.modal', (e) => {
        const $modal = $(e.target);
        const currentHint = $modal.find('.current');

        const updateSelected = (start, durationMinutes) => {
          currentHint.text(`Selected: ${formatTimeMoment(start)} - ${formatTimeMoment(start.add(durationMinutes, 'm'))} (${durationMinutes} minutes), ${formatDateMoment(start)}`);
        };
        if (dateTimePicker.date()) {
          updateSelected(dateTimePicker.date(), ($('#appointment_duration').val() || 0) / 60);
        }

        // Get clients
        const clients = getFormValues('clients[]');
        const teamMembers = getFormValues('teamMembers[]');
        const roomIDs = getFormValues('appointment.roomID');

        $modal.find('.appointment-freebusy-calendar').fullCalendar({
          ...CommonFullCalendarOptions,
          header: {
            left: 'prev,next today title',
            center: '',
            right: '',
          },
          height: 'parent',
          defaultView: 'agendaDay',
          defaultDate: dateTimePicker.date(),
          groupByResource: true,
          agendaDay: {
            titleFormat: 'dddd, MMM D, YYYY',
            columnFormat: 'dddd D/MM',
          },
          nowIndicator: true,
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

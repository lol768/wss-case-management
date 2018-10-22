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
  const $modal = $('#appointment-freebusy-modal');

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
            $modal.modal('show');
          }
        }),
    );

    $modal
      .on('shown.bs.modal', () => {
        const currentHint = $modal.find('.current');

        const selectedDuration = () => $form.find(':input[name="appointment.duration"]:checked').val();

        const updateSelected = (start, durationMinutes) => {
          currentHint.text(`Selected: ${formatTimeMoment(start)}${(durationMinutes > 0) ? ` - ${formatTimeMoment(start.add(durationMinutes, 'm'))} (${durationMinutes} minutes)` : ''}, ${formatDateMoment(start)}`);
        };

        if (dateTimePicker.date()) {
          updateSelected(dateTimePicker.date(), (selectedDuration() || 0) / 60);
        }

        const select = (start, end) => {
          $modal
            .data('start', moment(start))
            .data('duration', moment.duration(end.diff(start)).asSeconds());

          updateSelected(moment(start), moment.duration(end.diff(start)).asMinutes());
        };

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
          select,
          selectAllow: ({ start, end }) => {
            // Return false if we're selecting an invalid duration
            const duration = moment.duration(end.diff(start)).asSeconds();
            return !!$form.find(`:input[name="appointment.duration"][value="${duration}"]`).length;
          },
        });
      })
      .on('hidden.bs.modal', () => {
        $modal.find('.appointment-freebusy-calendar').fullCalendar('destroy');
      });

    $modal.find(':button[data-toggle="select"]').on('click', () => {
      const start = $modal.data('start');
      const duration = $modal.data('duration');

      dateTimePicker.date(start);
      $form.find(`:input[name="appointment.duration"][value="${duration}"]`).prop('checked', true);

      $modal.modal('hide');
    });
  });
}

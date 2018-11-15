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

  const getFormValuesArray = name => $form.find(`:input[name^="${name}["]`)
    .filter((i, el) => !!el.value)
    .map((i, el) => el.value)
    .get();

  $form.on('input change', () => {
    const clients = getFormValuesArray('clients');
    const teamMembers = getFormValuesArray('teamMembers');
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

          const clients = getFormValuesArray('clients');
          const teamMembers = getFormValuesArray('teamMembers');
          const rooms = getFormValues('appointment.roomID');

          if ([...clients, ...teamMembers, ...rooms].length) {
            $modal.modal('show');
          }
        }),
    );

    $modal
      .on('shown.bs.modal', () => {
        const currentHint = $modal.find('.current');
        const $calendar = $modal.find('.appointment-freebusy-calendar');
        const selectHighlightCalendarSourceId = 'selectHighlight';

        const selectedDuration = () => $form.find(':input[name="appointment.duration"]:checked').val();

        const updateSelected = () => {
          const start = $modal.data('start');
          const durationSeconds = $modal.data('duration');
          currentHint.text(`Selected: ${formatTimeMoment(start)}${(durationSeconds > 0) ? ` - ${formatTimeMoment(start.clone().add(durationSeconds, 's'))} (${durationSeconds / 60} minutes)` : ''}, ${formatDateMoment(start)}`);
        };

        if (dateTimePicker.date()) {
          $modal
            .data('start', dateTimePicker.date())
            .data('duration', (selectedDuration() || 0));
          updateSelected();
        }

        const select = (start, end) => {
          $modal
            .data('start', moment(start))
            .data('duration', moment.duration(end.diff(start)).asSeconds());

          updateSelected();
          $calendar.fullCalendar('refetchEventSources', selectHighlightCalendarSourceId);
        };

        // Get clients
        const clients = getFormValuesArray('clients');
        const teamMembers = getFormValuesArray('teamMembers');
        const roomIDs = getFormValues('appointment.roomID');

        $calendar.fullCalendar({
          ...CommonFullCalendarOptions,
          header: {
            left: 'prev,next today title',
            center: '',
            right: 'agendaWeek,agendaDay',
          },
          height: 'parent',
          defaultView: 'agendaDay',
          defaultDate: dateTimePicker.date(),
          groupByResource: true,
          views: {
            agendaWeek: {
              columnFormat: 'ddd D/MM',
            },
            agendaDay: {
              titleFormat: 'dddd, MMM D, YYYY',
              columnFormat: 'dddd D/MM',
            },
          },
          nowIndicator: true,
          eventSources: [
            {
              id: 'freebusy',
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
            },
            {
              id: selectHighlightCalendarSourceId,
              events: (start, end, timezone, callback) => {
                if (typeof $modal.data('start') !== 'undefined' && $modal.data('duration') > 0) {
                  callback([{
                    start: $modal.data('start'),
                    end: moment($modal.data('start')).add($modal.data('duration'), 's'),
                    rendering: 'background',
                  }]);
                } else {
                  callback([]);
                }
              },
            },
          ],
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

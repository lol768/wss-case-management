/* eslint-env browser */
import $ from 'jquery';
import _ from 'lodash-es';
import log from 'loglevel';
import { Calendar } from '@fullcalendar/core';
import dayGridPlugin from '@fullcalendar/daygrid';
import timeGridPlugin from '@fullcalendar/timegrid';
import moment from 'moment-timezone';
import { addQsToUrl, fetchWithCredentials, postJsonWithCredentials } from '@universityofwarwick/serverpipe';
import CommonFullCalendarOptions from './common-fullcalendar-options';
import tableViewPlugin from './fullcalendar-table-view';

export default function AppointmentCalendar(container) {
  const $calendar = $(container);

  const calendar = new Calendar({
    plugins: [dayGridPlugin, timeGridPlugin, tableViewPlugin],
    ...CommonFullCalendarOptions,
    header: {
      left: 'prev,next today title',
      center: '',
      right: 'agendaFourWeek,agendaWeek,agendaDay,table',
    },
    height: 'auto',
    contentHeight: 'auto',
    defaultView: $calendar.data('default-view') || 'agendaWeek',
    // CASE-303 Sticky view
    viewRender: (view, element) => {
      CommonFullCalendarOptions.viewRender(view, element);
      // Prevent un-necessary POST from just rendering
      if (view.name !== $calendar.data('default-view')) {
        $calendar.data('default-view', view.name);
        postJsonWithCredentials('/user-preferences/calendar-view', { calendarView: view.name });
      }
    },
    allDaySlot: false,
    nowIndicator: true,
    views: {
      agendaWeek: {
        columnFormat: 'ddd D/MM',
        selectable: true,
      },
      agendaFourWeek: {
        type: 'basic',
        duration: { weeks: 4 },
        buttonText: 'next 4 weeks',
        columnFormat: 'dddd',
        displayEventEnd: true,
      },
      agendaDay: {
        titleFormat: 'dddd, MMM D, YYYY',
        columnFormat: 'dddd D/MM',
        selectable: true,
      },
    },
    events: (start, end, timezone, callback) => {
      const apiEndpoint = $calendar.data('show-all-events') ? $calendar.data('events-all') : $calendar.data('events');

      fetchWithCredentials(
        addQsToUrl(apiEndpoint, {
          start: start.utc().toISOString(),
          end: end.utc().toISOString(),
          timezone,
        }),
      )
        .then(response => response.json())
        .catch((e) => {
          log.error(e);
          callback([]);
        })
        .then((response) => {
          if (response.success) {
            callback(_.map(response.data, event => ({
              ...event,

              // FullCalendar props
              id: event.id,
              resourceIds: [
                ..._.map(event.teamMembers, teamMember => teamMember.usercode),
                (event.location || {}).id,
              ],
              title: event.subject,
              allDay: false,
              start: event.start,
              end: event.end,
              url: event.url,
              className: `${event.team.id} ${event.state} ${(event.state === 'Attended' && event.outcome) ? 'has-outcome' : 'no-outcome'}`,
            })));
          } else {
            log.error(response.errors);
            callback([]);
          }
        });
    },
    eventRender: (event, $el) => {
      let $icon = $('<i />').addClass('fal');
      switch (event.state) {
        case 'Accepted':
          if (moment(event.end).isBefore(moment())) {
            $icon.addClass('fa-calendar-exclamation');
          } else {
            $icon.addClass('fa-calendar-check');
          }
          break;
        case 'Attended':
          if (event.outcome) {
            $icon.addClass('fa-calendar-star');
          } else {
            $icon.addClass('fa-calendar-exclamation');
          }
          break;
        case 'Cancelled':
          $icon.addClass('fa-calendar-times');
          break;
        case 'Provisional': // fall-through
        default:
          if (moment(event.end).isBefore(moment())) {
            $icon.addClass('fa-calendar-exclamation');
          } else {
            $icon = $('<span />').addClass('icon-stack icon-appointment-pending')
              .append($('<i />').addClass('fal fa-calendar fa-stack-2x'))
              .append($('<i />').addClass('fas fa-question fa-stack-1x'));
          }
      }

      const $time = $el.find('.fc-time');
      const $header = $('<div />').addClass('fc-event-header')
        .append($('<div />').addClass('fc-icon').append($icon));

      $time.before($header);
      $header.append($time);

      // CASE-347
      if ($el.find('.fc-title').text()) {
        const tooltip = `<div class="fc-event--tooltip">
            ${$time.data('full') || $time.text()}:
            ${(event.clients.length === 1) ? `<span class="fc-event--tooltip--name">${_.escape(event.clients[0].client.fullName || event.clients[0].client.universityID)}</span>` : `${event.clients.length} clients`},
            ${_.escape(event.appointmentType.description.toLowerCase())}
            with
            ${_.map(event.teamMembers, tm => `<span class="fc-event--tooltip--name">${_.escape(tm.fullName || event.team.name)}</span>`).join(', ')},
            ${_.escape(event.team.name)},
            ${(event.location && event.location.name) ? `${_.escape(event.location.name)}, ${_.escape(event.location.building)},` : ''}
            ${_.escape(event.purpose.description.toLowerCase())}
          </div>`;

        $el.tooltip({
          container: 'body',
          title: tooltip,
          placement: 'auto top',
          html: true,
          sanitize: false,
          delay: {
            show: 500,
            hide: 100,
          },
        });
      }
    },
    selectHelper: true,
    select: (start, end) => {
      // Start creating an appointment at the defined start/end time
      window.location = addQsToUrl($calendar.data('create'), {
        start: start.utc().toISOString(),
        duration: moment.duration(end.diff(start)).asSeconds(),
      });
    },
  });

  // If I'm inside a tabbed container, force a re-render when I become visible
  if ($calendar.closest('.tab-pane').length) {
    $('a[data-toggle="tab"]').on('shown.bs.tab', () => {
      if ($calendar.is(':visible')) {
        calendar.render();
      }
    });
  }

  // If I have a data-events-all, add a checkbox to toggle
  if ($calendar.data('events-all')) {
    const $checkbox = $('<input />').attr('type', 'checkbox');
    const $label = $('<label />').append($checkbox).append(' Show appointments for all teams');
    const $div = $('<div />').addClass('checkbox all-events-selector').append($label);
    $calendar.before($div);

    $checkbox.on('change', () => {
      $calendar.data('show-all-events', $checkbox.is(':checked'));
      calendar.refetchEvents();
    });
  }
}

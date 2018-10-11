/* eslint-env browser */
import $ from 'jquery';
import _ from 'lodash-es';
import log from 'loglevel';
import moment from 'moment-timezone';
import 'fullcalendar';
import { addQsToUrl, fetchWithCredentials } from './serverpipe';

export default function AppointmentCalendar(container) {
  const $container = $(container);
  $container.fullCalendar({
    header: {
      left: 'title',
      center: 'month,agendaWeek,agendaDay,listAll',
      right: 'prev,next',
    },
    themeSystem: 'bootstrap3',
    bootstrapGlyphicons: {
      close: ' fal fa-times',
      prev: ' fal fa-chevron-left',
      next: ' fal fa-chevron-right',
      prevYear: ' fal fa-backward',
      nextYear: ' fal fa-forward',
    },
    firstDay: 1,
    height: 'auto',
    contentHeight: 'auto',
    defaultView: 'agendaWeek',
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
    views: {
      agendaWeek: {
        columnFormat: 'ddd D/MM',
        selectable: true,
      },
      agendaDay: {
        titleFormat: 'dddd, MMM D, YYYY',
        columnFormat: 'dddd D/MM',
        selectable: true,
      },
    },
    events: (start, end, timezone, callback) => {
      fetchWithCredentials(
        addQsToUrl($container.data('events'), {
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
              id: event.id,
              resourceIds: [event.teamMember.usercode, (event.location || {}).name],
              title: event.subject,
              allDay: false,
              start: event.start,
              end: event.end,
              url: event.url,
              className: event.state,
            })));
          } else {
            log.error(response.errors);
            callback([]);
          }
        });
    },
    select: (start, end) => {
      // Start creating an appointment at the defined start/end time
      window.location = addQsToUrl($container.data('create'), {
        start: start.utc().toISOString(),
        duration: moment.duration(end.diff(start)).asSeconds(),
      });
    },
  });

  // If I'm inside a tabbed container, force a re-render when I become visible
  if ($container.closest('.tab-pane').length) {
    $('a[data-toggle="tab"]').on('shown.bs.tab', () => {
      if ($container.is(':visible')) {
        $container.fullCalendar('render');
      }
    });
  }
}

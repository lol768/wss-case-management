/* eslint-env browser */
import $ from 'jquery';
import log from 'loglevel';
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
    defaultView: 'agendaWeek',
    allDaySlot: false,
    slotEventOverlap: true,
    slotLabelFormat: 'HH:mm',
    timeFormat: 'HH:mm',
    scrollTime: '08:00:00',
    minTime: '08:00:00',
    maxTime: '19:00:00',
    weekends: false,
    nowIndicator: true,
    views: {
      agendaWeek: {
        columnFormat: 'ddd D/MM',
      },
      agendaDay: {
        titleFormat: 'dddd, MMM D, YYYY',
        columnFormat: 'dddd D/MM',
      },
    },
    events: (start, end, timezone, callback) => {
      fetchWithCredentials(
        addQsToUrl('/team/counselling/appointments', {
          start: start.format('ddd D MMM YYYY HH:mm'),
          end: start.format('ddd D MMM YYYY HH:mm'),
        }),
      )
        .then(response => response.json())
        .catch((e) => {
          log.error(e);
          callback([]);
        })
        .then((response) => {
          callback(response.data.events);
        });
    },
  });
}

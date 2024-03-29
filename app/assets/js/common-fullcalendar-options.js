/* eslint-env browser */
import $ from 'jquery';

const CommonFullCalendarOptions = {
  schedulerLicenseKey: 'CC-Attribution-NonCommercial-NoDerivatives',
  themeSystem: 'bootstrap3',
  bootstrapGlyphicons: {
    close: ' fal fa-times',
    prev: ' fal fa-chevron-left',
    next: ' fal fa-chevron-right',
    prevYear: ' fal fa-backward',
    nextYear: ' fal fa-forward',
  },
  firstDay: 1,
  allDaySlot: false,
  slotEventOverlap: false,
  slotLabelFormat: 'HH:mm',
  slotDuration: '00:15:00',
  slotLabelInterval: '01:00:00',
  timeFormat: 'HH:mm',
  minTime: '08:00:00',
  maxTime: '20:00:00',
  weekends: true, // https://warwick.slack.com/archives/CD7FF54NR/p1540539939002200
  timezone: 'Europe/London',
  viewRender: (view, element) => {
    if (view.type === 'agendaDay' && view.start.isSame(new Date(), 'day')) {
      const $header = $(element).closest('.fc').find('.fc-header-toolbar h2');
      // Check we haven't already done this otherwise refetching will add multiple times
      if (!$header.text().endsWith('(today)')) {
        $header.text(`${$header.text()} (today)`);
      }
    }
  },
};
export default CommonFullCalendarOptions;

/* eslint-env browser */

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
  slotEventOverlap: true,
  slotLabelFormat: 'HH:mm',
  slotDuration: '00:15:00',
  slotLabelInterval: '01:00:00',
  timeFormat: 'HH:mm',
  minTime: '08:00:00',
  maxTime: '19:00:00',
  weekends: false,
  timezone: 'Europe/London',
};
export default CommonFullCalendarOptions;

/* eslint-env browser */
import $ from 'jquery';
import moment from 'moment-timezone';

export default function DateRangeShortCuts(element) {
  $(element).on('click', (event) => {
    const { target } = event;
    const {
      start, end, startField, endField,
    } = target.dataset;

    if (start && end && startField && endField) {
      const $start = $(startField);
      const $end = $(endField);
      const startDate = new Date(start);
      const endDate = new Date(end);

      if ($start.length && startDate) {
        const picker = $start.closest('.input-group').data('DateTimePicker');
        const minDate = picker.minDate();
        const viableStartDate = minDate ? moment.max(startDate, minDate) : startDate;
        picker.date(viableStartDate);
      }

      if ($end.length && endDate) {
        const picker = $end.closest('.input-group').data('DateTimePicker');
        const maxDate = picker.maxDate();
        const viableEndDate = maxDate ? moment.min(endDate, maxDate) : endDate;
        picker.date(viableEndDate);
      }
    }
  });
}

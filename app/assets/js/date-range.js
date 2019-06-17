/* eslint-env browser */
import $ from 'jquery';

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
        $start.closest('.input-group').data('DateTimePicker').date(startDate);
      }

      if ($end.length && endDate) {
        $end.closest('.input-group').data('DateTimePicker').date(endDate);
      }
    }
  });
}

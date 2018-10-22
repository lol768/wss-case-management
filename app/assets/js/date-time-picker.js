/* eslint-env browser */

import $ from 'jquery';
import 'eonasdan-bootstrap-datetimepicker';
import moment from 'moment-timezone';

const icons = {
  time: 'fal fa-clock',
  date: 'fal fa-calendar-alt',
  up: 'fal fa-chevron-up',
  down: 'fal fa-chevron-down',
  previous: 'fal fa-chevron-left',
  next: 'fal fa-chevron-right',
  today: 'fal fa-crosshairs',
  clear: 'fal fa-trash',
  close: 'fal fa-times',
};

const dateTimeHiddenFieldFormat = 'YYYY-MM-DDTHH:mm';
const dateTimeTextFieldFormat = 'Do MMM YYYY, HH:mm';
const dayAndDateTimeTextFieldFormat = 'ddd Do MMM YYYY, HH:mm';

export function DateTimePicker(container) {
  const hiddenField = $(container).find('input[type=hidden]');
  const inputGroup = $(container).find('.input-group');
  const textField = inputGroup.find('input');
  const options = textField.data() || {};

  let currentDate;
  if (hiddenField.val()) {
    currentDate = moment(hiddenField.val(), dateTimeHiddenFieldFormat);
    textField.val(currentDate.format(dateTimeTextFieldFormat));
  }

  inputGroup.datetimepicker({
    format: dateTimeTextFieldFormat,
    icons,
    date: currentDate,
    sideBySide: true,
    allowInputToggle: true,
    stepping: 1,
    ...options,
  }).on('dp.change', ({ date }) => hiddenField.val(moment(date, dateTimeTextFieldFormat).format(dateTimeHiddenFieldFormat)));
}

export function InlineDateTimePicker(container) {
  const hiddenField = $(container).find('input[type=hidden]');
  const label = $(container).closest('.form-group').find('label.control-label');
  const div = hiddenField.next('div');
  const options = div.data() || {};

  const updateLabel = (newDate) => {
    let span = label.find('span.current');
    if (span.length === 0) {
      span = $('<span />').addClass('current');
      label.append(span);
    }

    span.text(`: ${newDate.format(dayAndDateTimeTextFieldFormat)}`);
  };

  let currentDate;
  if (hiddenField.val()) {
    currentDate = moment(hiddenField.val(), dateTimeHiddenFieldFormat);
  }

  div.datetimepicker({
    format: dateTimeTextFieldFormat,
    icons,
    date: currentDate,
    sideBySide: true,
    stepping: 1,
    useCurrent: true,
    inline: true,
    ...options,
  }).on('dp.change', ({ date }) => {
    const d = moment(date, dateTimeTextFieldFormat);
    hiddenField.val(d.format(dateTimeHiddenFieldFormat));
    updateLabel(d);
  }).trigger('init.datetimepicker');

  updateLabel(div.data('DateTimePicker').viewDate());
}

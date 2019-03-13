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

const dateHiddenFieldFormat = 'YYYY-MM-DD';
const dateTimeHiddenFieldFormat = 'YYYY-MM-DD[T]HH:mm';
const dateTextFieldFormat = 'Do MMM YYYY';
const dateTimeTextFieldFormat = 'Do MMM YYYY, HH:mm';
const dayAndDateTimeTextFieldFormat = 'ddd Do MMM YYYY, HH:mm';

const commonOptions = {
  locale: 'en-gb',
  icons,
  sideBySide: true,
  stepping: 1,
  useCurrent: true,
};

function PopupDatePicker(container, textFormat, hiddenFormat) {
  const hiddenField = $(container).find('input[type=hidden]');
  const inputGroup = $(container).find('.input-group');
  const textField = inputGroup.find('input');
  const options = textField.data() || {};
  delete options.aysOrig;

  let currentDate;
  if (hiddenField.val()) {
    currentDate = moment(hiddenField.val(), hiddenFormat);
    textField.val(currentDate.format(textFormat));
  }

  inputGroup.datetimepicker({
    ...commonOptions,
    format: textFormat,
    date: currentDate,
    allowInputToggle: true,
    ...options,
  }).on('dp.change', ({ date }) => {
    if (date) {
      hiddenField.val(date.format(hiddenFormat));
    }
  });
}

export function DatePicker(container) {
  PopupDatePicker(container, dateTextFieldFormat, dateHiddenFieldFormat);
}

export function DateTimePicker(container) {
  PopupDatePicker(container, dateTimeTextFieldFormat, dateTimeHiddenFieldFormat);
}

function InlinePicker(container, format) {
  const hiddenField = $(container).find('input[type=hidden]');
  const label = $(container).closest('.form-group').find('label.control-label');
  const div = hiddenField.next('div');
  const options = div.data() || {};
  delete options.aysOrig;

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
    ...commonOptions,
    format,
    date: currentDate,
    inline: true,
    ...options,
  }).on('dp.change', ({ date }) => {
    if (date) {
      hiddenField.val(date.format(dateTimeHiddenFieldFormat));
      updateLabel(date);
    }
  }).trigger('init.datetimepicker').trigger('dp.change');

  const viewDate = div.data('DateTimePicker').viewDate();
  hiddenField.val(viewDate.format(dateTimeHiddenFieldFormat));
  updateLabel(viewDate);
}

export function InlineDatePicker(container) {
  InlinePicker(container, dateTextFieldFormat);
}

export function InlineDateTimePicker(container) {
  InlinePicker(container, dateTimeTextFieldFormat);
}

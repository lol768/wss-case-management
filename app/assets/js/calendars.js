/* eslint-env browser */
import AppointmentCalendar from './appointment-calendar';
import AppointmentFreeBusyForm from './appointment-freebusy-calendar';
import * as dateTimePicker from './date-time-picker';

export function bindAppointmentCalendar(container) {
  AppointmentCalendar(container);
}

export function bindAppointmentFreeBusyForm(form) {
  AppointmentFreeBusyForm(form);
}

export function bindDatePicker(container) {
  dateTimePicker.DatePicker(container);
}

export function bindDateTimePicker(container) {
  dateTimePicker.DateTimePicker(container);
}

export function bindInlineDatePicker(container) {
  dateTimePicker.InlineDatePicker(container);
}

export function bindInlineDateTimePicker(container) {
  dateTimePicker.InlineDateTimePicker(container);
}

/* eslint-env browser */
import AppointmentCalendar from './appointment-calendar';
import AppointmentFreeBusyForm from './appointment-freebusy-calendar';

export function bindAppointmentCalendar(container) {
  AppointmentCalendar(container);
}

export function bindAppointmentFreeBusyForm(form) {
  AppointmentFreeBusyForm(form);
}

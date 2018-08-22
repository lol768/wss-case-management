import moment from 'moment-timezone';

const TZ = 'Europe/London';

export const localMoment = date => moment(date).tz(TZ);
export const localMomentUnix = date => moment.unix(date).tz(TZ);

/**
 * @typedef {Object} DateTimeOptions
 * @property {boolean} [printToday=false] - whether to include "Today" if it's today.
 * @property {boolean} [onlyWeekday=false] - when printing date, only print the weekday
 *  (even if in a different week).
 * @property {boolean} [shortDates=false] - when printing date, print full dates rather than short
 *  ones.
 */

const NO_OPTIONS = {};

const ONLY_TIME = 'HH:mm';
const SHORT_TODAY_TIME = '[Today] HH:mm';
const FULL_TODAY_TIME = '[Today], ddd Do MMMM, HH:mm';
const SHORT_YESTERDAY_TIME = '[Yesterday] HH:mm';
const FULL_YESTERDAY_TIME = '[Yesterday], ddd Do MMMM, HH:mm';
const SHORT_TOMORROW_TIME = '[Tomorrow] HH:mm';
const FULL_TOMORROW_TIME = '[Tomorrow], ddd Do MMMM, HH:mm';
const SHORT_WEEKDAY_TIME = 'ddd HH:mm';
const FULL_WEEKDAY_TIME = 'ddd Do MMMM, HH:mm';
const SHORT_DATE_WITHOUT_YEAR_TIME = 'ddd D MMM, HH:mm';
const FULL_DATE_WITHOUT_YEAR_TIME = 'ddd Do MMMM, HH:mm';
const SHORT_DATE_TIME = 'ddd D MMM YYYY, HH:mm';
const FULL_DATE_TIME = 'ddd Do MMMM YYYY, HH:mm';

const DATE_WEEKDAY = 'ddd';
const DATE_FULL_WITHOUT_YEAR = 'ddd D MMM';
const DATE_FULL = 'ddd D MMM YYYY';

export function formatDateMoment(then, now = localMoment()) {
  if (then.isSame(now.clone().subtract(1, 'day'), 'day')) {
    return 'yesterday';
  }

  if (then.isSame(now, 'day')) {
    return 'today';
  }

  if (then.isSame(now.clone().add(1, 'day'), 'day')) {
    return 'tomorrow';
  }

  if (then.isSame(now, 'isoWeek')) {
    return then.format(DATE_WEEKDAY);
  }

  if (then.isSame(now, 'year')) {
    return then.format(DATE_FULL_WITHOUT_YEAR);
  }

  return then.format(DATE_FULL);
}

export function formatTimeMoment(then) {
  return then.format('HH:mm');
}

/**
 * @param then The moment to format.
 * @param now A relative date for calculating today/tomorrow formatting.
 * @param {DateTimeOptions} options
 */
function formatDateTimeMoment(then, now, options = NO_OPTIONS) {
  const {
    printToday = false,
    onlyWeekday = false,
    shortDates = true,
  } = options;

  if (then.isSame(now, 'day')) {
    if (printToday) {
      return then.format(shortDates ? SHORT_TODAY_TIME : FULL_TODAY_TIME);
    }

    return then.format(ONLY_TIME);
  }

  if (then.isSame(now.clone().subtract(1, 'day'), 'day')) {
    return then.format(shortDates ? SHORT_YESTERDAY_TIME : FULL_YESTERDAY_TIME);
  }

  if (then.isSame(now.clone().add(1, 'day'), 'day')) {
    return then.format(shortDates ? SHORT_TOMORROW_TIME : FULL_TOMORROW_TIME);
  }

  if (onlyWeekday || then.isSame(now, 'isoWeek')) {
    return then.format(shortDates ? SHORT_WEEKDAY_TIME : FULL_WEEKDAY_TIME);
  }

  if (then.isSame(now, 'year')) {
    return then.format(shortDates ? SHORT_DATE_WITHOUT_YEAR_TIME : FULL_DATE_WITHOUT_YEAR_TIME);
  }

  return then.format(shortDates ? SHORT_DATE_TIME : FULL_DATE_TIME);
}

/**
 * @param {Date} date
 * @param {Date?} referenceDate=now
 * @param {DateTimeOptions} [options]
 * @returns {string}
 */
export function formatDateTime(date, referenceDate = new Date(), options) {
  if (date === undefined) throw new Error('No date specified'); // otherwise we render now :|

  return formatDateTimeMoment(localMoment(date), localMoment(referenceDate), options);
}

export function formatDate(date, referenceDate = new Date()) {
  if (date === undefined) throw new Error('No date specified'); // otherwise we render now :|
  return formatDateMoment(localMoment(date), localMoment(referenceDate));
}

export function formatTime(date) {
  if (date === undefined) throw new Error('No date specified'); // otherwise we render now :|
  return formatTimeMoment(localMoment(date));
}

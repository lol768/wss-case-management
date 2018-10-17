/* eslint-env browser */
import $ from 'jquery';
import _ from 'lodash-es';
import log from 'loglevel';
import moment from 'moment-timezone';
import 'fullcalendar';
import { addQsToUrl, fetchWithCredentials } from './serverpipe';
import { formatDateMoment, formatTimeMoment } from './dateFormats';

const FC = $.fullCalendar; // a reference to FullCalendar's root namespace
const {
  View,
  EventPointing,
  EventRenderer,
  Scroller,
  UnzonedRange,
  htmlEscape,
} = FC; // the class that all views must inherit from

class TableView extends View {
  constructor(calendar, viewSpec) {
    super(calendar, viewSpec);

    this.segSelector = '.fc-table-item';
    this.scroller = new Scroller({
      overflowX: 'hidden',
      overflowY: 'auto',
    });
  }

  renderSkeleton() {
    this.el.addClass('fc-table-view');

    this.scroller.render();
    this.scroller.el.appendTo(this.el);

    this.contentEl = this.scroller.scrollEl; // shortcut
  }

  unrenderSkeleton() {
    this.scroller.destroy(); // will remove the Grid too
  }

  updateSize(totalHeight, isAuto, isResize) {
    super.updateSize(totalHeight, isAuto, isResize);

    this.scroller.clear(); // sets height to 'auto' and clears overflow

    if (!isAuto) {
      this.scroller.setHeight(this.computeScrollerHeight(totalHeight));
    }
  }

  renderEmptyMessage() {
    this.contentEl.html(`<div class="fc-table-empty">${htmlEscape(this.opt('noEventsMessage'))}</div>`);
  }

  renderDates(dateProfile) {
    const { calendar } = this;
    const dayStart = calendar.msToUtcMoment(dateProfile.renderUnzonedRange.startMs, true);
    const viewEnd = calendar.msToUtcMoment(dateProfile.renderUnzonedRange.endMs, true);
    const dayDates = [];
    const dayRanges = [];

    while (dayStart < viewEnd) {
      dayDates.push(dayStart.clone());

      dayRanges.push(new UnzonedRange(
        dayStart,
        dayStart.clone().add(1, 'day'),
      ));

      dayStart.add(1, 'day');
    }

    this.dayDates = dayDates;
    this.dayRanges = dayRanges;

    // all real rendering happens in EventRenderer
  }

  componentFootprintToSegs(footprint) {
    const { dayRanges } = this;
    const segs = [];

    for (let dayIndex = 0; dayIndex < dayRanges.length; dayIndex += 1) {
      const segRange = footprint.unzonedRange.intersect(dayRanges[dayIndex]);

      if (segRange) {
        const seg = {
          startMs: segRange.startMs,
          endMs: segRange.endMs,
          isStart: segRange.isStart,
          isEnd: segRange.isEnd,
          dayIndex,
        };

        segs.push(seg);

        // detect when footprint won't go fully into the next day,
        // and mutate the latest seg to the be the end.
        if (
          !seg.isEnd
          && dayIndex + 1 < dayRanges.length
          && footprint.unzonedRange.endMs < dayRanges[dayIndex + 1].startMs + this.nextDayThreshold
        ) {
          seg.endMs = footprint.unzonedRange.endMs;
          seg.isEnd = true;
          break;
        }
      }
    }

    return segs;
  }

  // render the event segments in the view
  renderSegList(allSegs) {
    const segsByDay = TableView.groupSegsByDay(allSegs); // sparse array
    const tableEl = $('<table class="fc-table-table table table-default"><tbody/></table>');
    const tbodyEl = tableEl.find('tbody');

    for (let dayIndex = 0; dayIndex < segsByDay.length; dayIndex += 1) {
      const daySegs = segsByDay[dayIndex];

      if (daySegs) { // sparse array, so might be undefined
        // append a day header
        const { weekNumber } = daySegs[0].footprint.eventDef.miscProps;

        tbodyEl.append(this.dayHeaderHtml(this.dayDates[dayIndex], weekNumber));

        this.eventRenderer.sortEventSegs(daySegs);

        for (let i = 0; i < daySegs.length; i += 1) {
          tbodyEl.append(daySegs[i].el); // append event row
        }
      }
    }

    this.contentEl.empty().append(tableEl);
  }

  // Returns a sparse array of arrays, segs grouped by their dayIndex
  static groupSegsByDay(segs) {
    const segsByDay = []; // sparse array

    for (let i = 0; i < segs.length; i += 1) {
      const seg = segs[i];
      (segsByDay[seg.dayIndex] || (segsByDay[seg.dayIndex] = []))
        .push(seg);
    }

    return segsByDay;
  }

  // generates the HTML for the day headers that live amongst the event rows
  dayHeaderHtml(dayDate, weekNumber) {
    const weekHtml = (weekNumber !== this.lastWeekNumber) ? `<span class='fc-table-heading--week'>(week ${htmlEscape(weekNumber)})</span>` : '';
    this.lastWeekNumber = weekNumber;

    return `<tr class="fc-table-heading" data-date="${dayDate.format('YYYY-MM-DD')}">
        <td class="${this.calendar.theme.getClass('widgetHeader')}" colspan="5">
          ${this.buildGotoAnchorHtml(dayDate, { class: 'fc-table-heading--date' }, htmlEscape(formatDateMoment(dayDate)))}
          ${weekHtml}
        </td>
      </tr>`;
  }
}

class TableEventRenderer extends EventRenderer {
  renderFgSegs(segs) {
    if (!segs.length) {
      this.component.renderEmptyMessage();
    } else {
      this.component.renderSegList(segs);
    }
  }

  // generates the HTML for a single event row
  fgSegHtml(seg) {
    const eventFootprint = seg.footprint;
    const { eventDef } = eventFootprint;
    const { url } = eventDef;
    const classes = ['fc-table-item'].concat(this.getClasses(eventDef));

    if (url) {
      classes.push('fc-has-url');
    }

    const { start, end } = eventFootprint.eventInstance.dateProfile;
    const {
      clients,
      key,
      appointmentType,
      teamMember,
      location,
      state,
    } = eventDef.miscProps;

    return `<tr class="${classes.join(' ')}">
        <td class="fc-table-item--time col-sm-1">
          <span class="fc-table-item--time--start-time">${htmlEscape(formatTimeMoment(start))}</span>
          <br />
          <span class="fc-table-item--time--end-time">${htmlEscape(formatTimeMoment(end))}</span>
        </td>
        <td class="fc-table-item--title col-sm-4">
          <span class="fc-table-item--title--clients">
            ${_.map(clients, client => `<a href="/team/client/${client.client.universityID}">${htmlEscape(client.client.fullName)}</a>`).join('<br />')}
          </span>
          <br />
          <span class="fc-table-item--title--key">${htmlEscape(key)}</span>
        </td>
        <td class="fa-table-item--details col-sm-4">
          <span class="fc-table-item--details--type">${htmlEscape(appointmentType.description)}</span>
          <br />
          <span class="fc-table-item--details--team-member">with ${htmlEscape(teamMember.fullName)}</span>
        </td>
        <td class="fa-table-item--location col-sm-2">
          ${location ? htmlEscape(location.name) : ''}
        </td>
        <td class="fa-table-item--state col-sm-1">
          ${htmlEscape(state)}
        </td>
      </tr>`;
  }
}

class TableEventPointing extends EventPointing {
  // for events with a url, the whole <tr> should be clickable,
  // but it's impossible to wrap with an <a> tag. simulate this.
  handleClick(seg, ev) {
    super.handleClick(seg, ev); // might prevent the default action

    // not clicking on or within an <a> with an href
    if (!$(ev.target).closest('a[href]').length) {
      const { url } = seg.footprint.eventDef;

      if (url && !ev.isDefaultPrevented()) { // jsEvent not cancelled in handler
        window.location.href = url; // simulate link click
      }
    }
  }
}

TableView.prototype.eventRendererClass = TableEventRenderer;
TableView.prototype.eventPointingClass = TableEventPointing;

FC.views.table = {
  class: TableView,
  buttonTextKey: 'table',
  duration: { days: 7 },
  defaults: {
    buttonText: 'table',
    noEventsMessage: 'No appointments to display',
  },
};

export default function AppointmentCalendar(container) {
  const $calendar = $(container);
  $calendar.fullCalendar({
    schedulerLicenseKey: 'CC-Attribution-NonCommercial-NoDerivatives',
    header: {
      left: 'title',
      center: 'agendaFourWeek,agendaWeek,agendaDay,table',
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
    height: 'auto',
    contentHeight: 'auto',
    defaultView: 'agendaWeek',
    allDaySlot: false,
    slotEventOverlap: true,
    slotLabelFormat: 'HH:mm',
    slotDuration: '00:15:00',
    slotLabelInterval: '01:00:00',
    timeFormat: 'HH:mm',
    minTime: '08:00:00',
    maxTime: '19:00:00',
    weekends: false,
    nowIndicator: true,
    timezone: 'Europe/London',
    views: {
      agendaWeek: {
        columnFormat: 'ddd D/MM',
        selectable: true,
      },
      agendaFourWeek: {
        type: 'basic',
        duration: { weeks: 4 },
        buttonText: 'next 4 weeks',
        columnFormat: 'ddd D/MM',
      },
      agendaDay: {
        titleFormat: 'dddd, MMM D, YYYY',
        columnFormat: 'dddd D/MM',
        selectable: true,
      },
    },
    events: (start, end, timezone, callback) => {
      fetchWithCredentials(
        addQsToUrl($calendar.data('events'), {
          start: start.utc().toISOString(),
          end: end.utc().toISOString(),
          timezone,
        }),
      )
        .then(response => response.json())
        .catch((e) => {
          log.error(e);
          callback([]);
        })
        .then((response) => {
          if (response.success) {
            callback(_.map(response.data, event => ({
              ...event,

              // FullCalendar props
              id: event.id,
              resourceIds: [event.teamMember.usercode, (event.location || {}).name],
              title: event.subject,
              allDay: false,
              start: event.start,
              end: event.end,
              url: event.url,
              className: event.state,
            })));
          } else {
            log.error(response.errors);
            callback([]);
          }
        });
    },
    select: (start, end) => {
      // Start creating an appointment at the defined start/end time
      window.location = addQsToUrl($calendar.data('create'), {
        start: start.utc().toISOString(),
        duration: moment.duration(end.diff(start)).asSeconds(),
      });
    },
  });

  // If I'm inside a tabbed container, force a re-render when I become visible
  if ($calendar.closest('.tab-pane').length) {
    $('a[data-toggle="tab"]').on('shown.bs.tab', () => {
      if ($calendar.is(':visible')) {
        $calendar.fullCalendar('render');
      }
    });
  }
}

/* eslint-env browser */
import $ from 'jquery';
import _ from 'lodash-es';
import log from 'loglevel';
import moment from 'moment-timezone';
import 'fullcalendar';
import { addQsToUrl, fetchWithCredentials } from './serverpipe';

const FC = $.fullCalendar; // a reference to FullCalendar's root namespace
const {
  View,
  EventPointing,
  EventRenderer,
  Scroller,
  htmlEscape,
} = FC; // the class that all views must inherit from

class TableView extends View {
  constructor(calendar, viewSpec) {
    super(calendar, viewSpec);

    this.scroller = new Scroller({
      overflowX: 'hidden',
      overflowY: 'auto',
    });
  }

  renderSkeleton() {
    this.el.addClass(`fc-list-view ${this.calendar.theme.getClass('listView')}`);

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
    this.contentEl.html(
      `<div class="fc-list-empty-wrap2">
        <div class="fc-list-empty-wrap1">
          <div class="fc-list-empty">
            ${htmlEscape(this.opt('noEventsMessage'))}
          </div>
        </div>
      </div>`,
    );
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
    const { view } = this;
    const { calendar } = view;
    const { theme } = calendar;
    const eventFootprint = seg.footprint;
    const { eventDef, componentFootprint } = eventFootprint;
    const { url } = eventDef;
    const classes = ['fc-list-item'].concat(this.getClasses(eventDef));
    const bgColor = this.getBgColor(eventDef);
    let timeHtml;

    if (componentFootprint.isAllDay) {
      timeHtml = view.getAllDayHtml();
    } else if (view.isMultiDayRange(componentFootprint.unzonedRange)) {
      if (seg.isStart || seg.isEnd) { // outer segment that probably lasts part of the day
        timeHtml = htmlEscape(this._getTimeText( // eslint-disable-line no-underscore-dangle
          calendar.msToMoment(seg.startMs),
          calendar.msToMoment(seg.endMs),
          componentFootprint.isAllDay,
        ));
      } else { // inner segment that lasts the whole day
        timeHtml = view.getAllDayHtml();
      }
    } else {
      // Display the normal time text for the *event's* times
      timeHtml = htmlEscape(this.getTimeText(eventFootprint));
    }

    if (url) {
      classes.push('fc-has-url');
    }

    return `<tr class="${classes.join(' ')}">
      ${this.displayEventTime ? `<td class="fc-list-item-time ${theme.getClass('widgetContent')}">${timeHtml || ''}</td>` : ''}
      <td class="fc-list-item-marker ${theme.getClass('widgetContent')}">
        <span class="fc-event-dot" ${bgColor ? ` style="background-color:${bgColor}"` : ''}></span>
      </td>
      <td class="fc-list-item-title ${theme.getClass('widgetContent')}">
      <a${url ? ` href="${htmlEscape(url)}"` : ''}>
        ${htmlEscape(eventDef.title || '')}
      </a>
      </td>
      </tr>`;
  }


  // like "4:00am"
  computeEventTimeFormat() {
    return this.opt('mediumTimeFormat');
  }
}

class TableEventPointing extends EventPointing {

}

TableView.prototype.eventRendererClass = TableEventRenderer;
TableView.prototype.eventPointingClass = TableEventPointing;

// const TableView = View.extend({
//   // called once when the view is instantiated, when the user switches to the view.
//   // initialize member variables or do other setup tasks.
//   initialize: () => log.error('Not implemented: initialize()'),
//
//   // responsible for displaying the skeleton of the view within the already-defined
//   // this.el, a jQuery element.
//   render: () => log.error('Not implemented: render()'),
//
//   // responsible for adjusting the pixel-height of the view. if isAuto is true, the
//   // view may be its natural height, and `height` becomes merely a suggestion.
//   setHeight: (height, isAuto) => log.error(`Not implemented: setHeight(${height}, ${isAuto})`),
//
//   // reponsible for rendering the given Event Objects
//   renderEvents: events => log.error(`Not implemented: renderEvents(${events})`),
//
//   // responsible for undoing everything in renderEvents
//   destroyEvents: log.error('Not implemented: destroyEvents()'),
//
//   // accepts a {start,end} object made of Moments, and must render the selection
//   renderSelection: range => log.error(`Not implemented: renderSelection(${range})`),
//
//   // responsible for undoing everything in renderSelection
//   destroySelection: () => log.error('Not implemented: destroySelection()'),
// });

FC.views.table = {
  class: TableView,
  buttonTextKey: 'table',
  defaults: {
    buttonText: 'table',
    tableDayFormat: 'LL',
    noEventsMessage: 'No appointments to display',
    duration: { years: 1 },
  },
};

export default function AppointmentCalendar(container) {
  const $container = $(container);
  $container.fullCalendar({
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
        selectable: true,
      },
      agendaDay: {
        titleFormat: 'dddd, MMM D, YYYY',
        columnFormat: 'dddd D/MM',
        selectable: true,
      },
    },
    events: (start, end, timezone, callback) => {
      fetchWithCredentials(
        addQsToUrl($container.data('events'), {
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
      window.location = addQsToUrl($container.data('create'), {
        start: start.utc().toISOString(),
        duration: moment.duration(end.diff(start)).asSeconds(),
      });
    },
  });

  // If I'm inside a tabbed container, force a re-render when I become visible
  if ($container.closest('.tab-pane').length) {
    $('a[data-toggle="tab"]').on('shown.bs.tab', () => {
      if ($container.is(':visible')) {
        $container.fullCalendar('render');
      }
    });
  }
}

/* eslint-env browser */
import './polyfills';

import $ from 'jquery';
import './jquery.are-you-sure';
import FieldHistory from './field-history';
import * as flexiPicker from './flexi-picker';
import * as memberPicker from './member-picker';
import ClientSearch from './client-search';
import UserListPopovers from './user-list-popovers';
import MessageThreads from './message-threads';
import CasePicker from './case-picker';
import MultiplePickers from './multiple-picker';
import CaseSearch from './case-search';
import EnquirySearch from './enquiry-search';
import AppointmentSearch from './appointment-search';
import AppointmentCalendar from './appointment-calendar';
import AppointmentFreeBusyForm from './appointment-freebusy-calendar';
import { DateTimePicker, InlineDateTimePicker } from './date-time-picker';
import PaginatingTable from './paginating-table';

function closePopover($popover) {
  const $creator = $popover.data('creator');
  if ($creator) {
    $creator.popover('hide');
  }
}

/**
 * Attach handlers to all elements inside $scope. All jQuery selects
 * must be scoped to $scope, and you should only call bindTo on content
 * that you know has not had handlers attached already (either a whole page
 * that's just been loaded, or a piece of HTML you've just loaded into the
 * document dynamically)
 */
function bindTo($scope) {
  $('[data-toggle="popover"]', $scope).popover();

  $('i.icon-tooltip, .btn-tooltip', $scope).tooltip({
    delay: { show: 500, hide: 100 },
    placement: 'auto top',
  });

  UserListPopovers($scope);

  $('.field-history', $scope).each((i, container) => {
    FieldHistory(container);
  });

  $('.client-search', $scope).each((i, container) => {
    ClientSearch(container);
  });

  $('.message-threads', $scope).each((i, container) => {
    MessageThreads(container);
  });

  $('.case-picker', $scope).each((i, container) => {
    CasePicker(container);
  });

  flexiPicker.bindTo($scope);

  memberPicker.bindTo($scope);

  $('.case-picker-collection', $scope).each((i, collection) => {
    MultiplePickers(collection, (element) => {
      CasePicker(element);
    });
  });

  $('.case-search', $scope).each((i, container) => {
    CaseSearch(container);
  });

  $('.enquiry-search', $scope).each((i, container) => {
    EnquirySearch(container);
  });

  $('.appointment-search', $scope).each((i, container) => {
    AppointmentSearch(container);
  });

  $('.appointment-calendar', $scope).each((i, container) => {
    AppointmentCalendar(container);
  });

  $('.appointment-freebusy-form', $scope).each((i, container) => {
    AppointmentFreeBusyForm(container);
  });

  $('.datetimepicker', $scope).each((i, container) => {
    DateTimePicker(container);
  });

  $('.datetimepicker-inline', $scope).each((i, container) => {
    InlineDateTimePicker(container);
  });


  $('form', $scope)
    .not('.no-dirty-check')
    .areYouSure()
    .end()
    .not('no-double-submit-protection')
    .on('submit', (e) => {
      $(e.target).find('button[type=submit]').prop('disabled', true);
    });

  $('.toggle-element', $scope).each((i, container) => {
    const $this = $(container);
    const $target = $($this.data('target'));
    const shownLabel = $this.data('shownLabel');
    const hiddenLabel = $this.text();
    $this.on('click', () => {
      if ($target.hasClass('hidden')) {
        $target.removeClass('hidden');
        $this.html(shownLabel);
      } else {
        $target.addClass('hidden');
        $this.html(hiddenLabel);
      }
    });
  });

  $('input[type="checkbox"][data-toggle="optional-subform"][data-target]', $scope).each((i, el) => {
    const $checkbox = $(el);
    const $target = $($checkbox.data('target'));

    const update = () => {
      if ($checkbox.is(':checked')) {
        $target.show().find(':input').prop('disabled', false);
      } else {
        $target.hide().find(':input').prop('disabled', true);
      }
    };

    $checkbox.on('input change', update);
    update();
  });

  $('input[type="radio"][data-toggle="optional-subform"][data-target]', $scope).each((i, el) => {
    const $radio = $(el);
    const $target = $($radio.data('target'));

    const update = () => {
      if ($radio.val() === $radio.data('toggle-value')) {
        $target.show().find(':input').prop('disabled', false);
      } else {
        $target.hide().find(':input').prop('disabled', true);
      }
    };

    $radio.on('input change', update);
    if ($radio.is(':checked')) update();
  });

  $('details[data-toggle="load"][data-href][data-target]', $scope).on('toggle', function load() {
    const $details = $(this);
    if (this.open && !$details.data('loading')) {
      $details.data('loading', true);
      const $target = $details.find($details.data('target'));
      $target.load($details.data('href'), (text, status, xhr) => {
        if (status === 'error') {
          $target.text(`Unable to load content: ${xhr.statusText || xhr.status || 'error'}`);
        } else {
          bindTo($target);
        }
      });
    }
  });

  // In browsers that don't support details, trigger a load immediately
  // This is a non-perfect feature detection but we don't care about early webkit, just IE/Edge
  if (!('open' in document.createElement('details'))) {
    $('details[data-toggle="load"][data-href][data-target]', $scope)
      .each(function setOpen() { this.open = true; })
      .trigger('toggle');
  }

  // be sure to bind the confirm-submit handler before other handlers on submit buttons
  $('a[data-toggle~="confirm-submit"][data-message], :button[data-toggle~="confirm-submit"][data-message]', $scope).on('click', function confirmBeforeSubmit(event) {
    const $button = $(this);
    // eslint-disable-next-line no-alert
    if (!window.confirm($button.data('message'))) {
      event.preventDefault();
      event.stopImmediatePropagation();
    }
  });

  $('a[data-toggle~="remove-submit"][data-target], :button[data-toggle~="remove-submit"][data-target]', $scope).on('click', function removeAndSubmit(event) {
    const $button = $(this);
    const $form = $button.closest('form');
    $button.closest($button.data('target')).find(':input').remove();
    event.preventDefault();
    $form.submit();
  });

  $(':input.change-submit', $scope).on('change', function submitOnChange() {
    const $input = $(this);
    const $form = $input.closest('form');
    $form.submit();
  });

  $('.map-link', $scope).each((i, link) => {
    const $link = $(link);
    const mapUrl = `https://campus.warwick.ac.uk/?lite=1&slid=${encodeURIComponent($link.data('lid'))}`;
    $link.popover({
      trigger: 'click',
      container: 'body',
      template: '<div class="popover wide"><div class="arrow"></div><div class="popover-inner"><div class="popover-content"><p></p></div></div></div>',
      html: true,
      content: `<iframe width="300" height="400" frameborder="0" src="${mapUrl}"></iframe>`,
      placement: 'auto bottom',
    });
    $link.on('click', (e) => {
      e.preventDefault();
      e.stopPropagation();
    });
  });

  $('details.async[class*="details--section"], div.async[class*="details--section"]', $scope).each((i, target) => {
    const $target = $(target);
    $target.find('div.content').load($target.data('href'), (text, status, xhr) => {
      if (status === 'error') {
        $target.find('div.content').text(`Unable to load content: ${xhr.statusText || xhr.status || 'error'}`);
      } else {
        const $count = $target.find('.control-label .count');
        if ($count.length > 0) {
          const count = $target.find('div.content [data-count]').data('count');
          if (count !== undefined) {
            $count.text(`(${count})`);
          }
        }
        bindTo($target);
      }
    });
  });

  $('table.table-paginated', $scope).each((i, container) => PaginatingTable(container));

  function loadTabPanelContent($tabPanel) {
    if (!$tabPanel.data('tabPanelLoaded')) {
      $tabPanel
        .data('tabPanelLoaded', true)
        .empty()
        .append('<i class="fas fa-spinner fa-pulse"></i> Loading&hellip;')
        .load($tabPanel.data('href'), (text, status, xhr) => {
          if (status === 'error') {
            $tabPanel.text(`Unable to load content: ${xhr.statusText || xhr.status || 'error'}`);
          } else {
            bindTo($tabPanel);
          }
        });
    }
  }

  $('a[data-toggle="tab"]', $scope).on('show.bs.tab', (e) => {
    const $tabPanel = $($(e.target).attr('href'), $scope);
    if ($tabPanel && $tabPanel.data('href')) {
      loadTabPanelContent($tabPanel);
    }
  });

  $('.tab-pane.active[data-href]', $scope).each((i, tabPanel) => {
    loadTabPanelContent($(tabPanel));
  });
}

$(() => {
  if (!('open' in document.createElement('details'))) {
    $('html').addClass('no-details');
  } else {
    $('html').addClass('details');
  }

  // Apply to all content loaded non-AJAXically
  bindTo($('#main'));

  // Any selectors below should only be for things that we know won't be inserted into the
  // page after DOM ready.

  // Don't scroll when clicking on tabs
  $('.nav-tabs a').off('shown.bs.tab.id7Navigation').on('shown.bs.tab.id7navigation', (e) => {
    window.history.replaceState({}, null, e.target.hash);
  });

  // Dismiss popovers when clicking away
  $('html')
    .on('shown.bs.popover', (e) => {
      const $po = $(e.target).popover().data('bs.popover').tip();
      $po.data('creator', $(e.target));
    })
    .on('click.popoverDismiss', (e) => {
      const $target = $(e.target);

      // if clicking anywhere other than the popover itself
      if ($target.closest('.popover').length === 0 && $(e.target).closest('.has-popover').length === 0) {
        $('.popover').each((i, popover) => closePopover($(popover)));
      } else if ($target.closest('.close').length > 0) {
        closePopover($target.closest('.popover'));
      }
    })
    .on('keyup.popoverDismiss', (e) => {
      const key = e.which || e.keyCode;
      if (key === 27) {
        $('.popover').each((i, popover) => closePopover($(popover)));
      }
    });
});

/* eslint-env browser */

import $ from 'jquery';
import 'core-js/modules/es6.object.assign';
import './jquery.are-you-sure';
import FieldHistory from './field-history';
import './flexi-picker';
import ClientSearch from './client-search';
import UserListPopovers from './user-list-popovers';
import MessageThreads from './message-threads';
import CasePicker from './case-picker';
import CaseSearch from './case-search';
import EnquirySearch from './enquiry-search';

function closePopover($popover) {
  const $creator = $popover.data('creator');
  if ($creator) {
    $creator.popover('hide');
  }
}

$(() => {
  $('[data-toggle="popover"]').popover();

  UserListPopovers();

  $('.field-history').each((i, container) => {
    FieldHistory(container);
  });

  $('.client-search').each((i, container) => {
    ClientSearch(container);
  });

  $('.panel-group.enquiries').each((i, container) => {
    MessageThreads(container);
  });

  $('.case-picker').each((i, container) => {
    CasePicker(container);
  });

  $('.case-search').each((i, container) => {
    CaseSearch(container);
  });

  $('.enquiry-search').each((i, container) => {
    EnquirySearch(container);
  });

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
    }).on('keyup.popoverDismiss', (e) => {
      const key = e.which || e.keyCode;
      if (key === 27) {
        $('.popover').each((i, popover) => closePopover($(popover)));
      }
    });

  $('form.dirty-check').areYouSure();

  $('.toggle-element').each((i, container) => {
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

  $('input[type="checkbox"][data-toggle="optional-subform"][data-target]').each((i, el) => {
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

  $('details[data-toggle="load"][data-href][data-target]').on('toggle', function load() {
    const $details = $(this);
    if (this.open && !$details.data('loading')) {
      $details.data('loading', true);
      $details.find($details.data('target')).load($details.data('href'));
    }
  });

  $(':button[data-toggle="remove-submit"][data-target]').on('click', function removeAndSubmit() {
    const $button = $(this);
    const $form = $button.closest('form');
    $button.closest($button.data('target')).find(':input').remove();
    $form.submit();
  });

  $(':input.change-submit').on('change', function submitOnChange() {
    const $input = $(this);
    const $form = $input.closest('form');
    $form.submit();
  });
});

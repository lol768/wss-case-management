/* eslint-env browser */

import $ from 'jquery';
import 'core-js/modules/es6.object.assign';
import './jquery.are-you-sure';
import FieldHistory from './field-history';
import './flexi-picker';
import ClientSearch from './client-search';
import UserListPopovers from './user-list-popovers';
import MessageThreads from './message-threads';

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
});

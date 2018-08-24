/* eslint-env browser */

import $ from 'jquery';
import 'core-js/modules/es6.object.assign';
import FieldHistory from './field-history';
import ClientSearch from './client-search';

function closePopover($popover) {
  const $creator = $popover.data('creator');
  if ($creator) {
    $creator.popover('hide');
  }
}

$(() => {
  $('[data-toggle="popover"]').popover();

  $('.field-history').each((i, container) => {
    FieldHistory(container);
  });

  $('.client-search').each((i, container) => {
    ClientSearch(container);
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
});

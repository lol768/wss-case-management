/* eslint-env browser */

import $ from 'jquery';

import 'core-js/modules/es6.object.assign';
import FieldHistory from './field-history';

$(() => {
  $('[data-toggle="popover"]').popover();

  $('.field-history').each((i, container) => {
    FieldHistory(container);
  });

  $('body').on('click', '.popover .close', (e) => {
    $(e.target).closest('.popover').data('bs.popover').$element.popover('hide');
  });
});

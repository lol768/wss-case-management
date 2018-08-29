/* eslint-env browser */

import $ from 'jquery';

import 'core-js/modules/es6.object.assign';
import './jquery.are-you-sure';
import FieldHistory from './field-history';
import ClientSearch from './client-search';

$(() => {
  $('[data-toggle="popover"]').popover();

  $('.field-history').each((i, container) => {
    FieldHistory(container);
  });

  $('.client-search').each((i, container) => {
    ClientSearch(container);
  });

  $('body').on('click', '.popover .close', (e) => {
    $(e.target).closest('.popover').data('bs.popover').$element.popover('hide');
  });

  $('form.dirty-check').areYouSure();
});

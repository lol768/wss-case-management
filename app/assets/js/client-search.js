/* eslint-env browser */
import $ from 'jquery';
import _ from 'lodash-es';
import 'bootstrap-3-typeahead';

function transformItem(input) {
  const item = input;

  item.title = item.name;

  item.description = `${item.value}, ${item.userType}`;

  if (item.department !== null) {
    item.description += `, ${item.department}`;
  }
}

export default function ClientSearch(container) {
  let currentQuery = null;
  const $container = $(container);
  const url = $container.prop('action');

  function doSearch(query, callback) {
    currentQuery = query;
    $.get(`${url}?query=${encodeURIComponent(query)}`).done((response) => {
      if (currentQuery === query) {
        if (response.success) {
          $.each(response.data.results, (i, item) => transformItem(item));
          callback(response.data.results || []);
        }
      }
    });
  }

  const $typeahead = $container.find('input.form-control').typeahead({
    source: (query, callback) => {
      doSearch(query, callback);
    },
    delay: 200,
    matcher: () => true, // All data received from the server matches the query
    displayText: item => `<div class="client-search-result">
      <div class="media">
        <div class="media-left">
            ${(item.photo) ? `<img class="media-object" src="${_.escape(item.photo)}" />` : ''}
        </div>
        <div class="media-body">
          <span class="title">${_.escape(item.title)}</span>
          <div class="description">
            ${(typeof (item.description) !== 'undefined' ? _.escape(item.description) : '')}
          </div>
        </div>
      </div>
    </div>`,
    highlighter: html => html,
    changeInputOnSelect: false,
    changeInputOnMove: false,
    followLinkOnSelect: true,
    selectOnBlur: false,
    showHintOnFocus: true,
    itemLink: item => `/team/client/${_.escape(item.value)}`,
    afterSelect: () => {
      $typeahead.trigger('blur');
    },
  });

  // You can change the 'appendTo' option on the typeahead,
  // but it positions in a stupid way, so lets do it properly
  const instance = $typeahead.data('typeahead');
  instance.show = () => {
    const pos = $.extend({}, instance.$element.position(), {
      height: instance.$element[0].offsetHeight,
    });

    instance.$menu.appendTo($container).show().css({
      top: pos.top + pos.height,
      left: pos.left,
    });

    instance.shown = true;
    return this;
  };

  $container.on('submit', (e) => {
    e.preventDefault();
  });
}

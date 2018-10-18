import $ from 'jquery';
import log from 'loglevel';
import _ from 'lodash-es';
import 'bootstrap-3-typeahead';
import { postJsonWithCredentials } from './serverpipe';
import RichResultField from './rich-result-field';
import MultiplePickers from './multiple-picker';

function MemberPicker(element) {
  let currentQuery = null;
  const $element = $(element);

  // Might have manually wired this element up with MemberPicker,
  // but add the class for CSS style purposes.
  if (!$element.hasClass('member-picker')) {
    $element.addClass('member-picker');
  }

  // Disable browser autocomplete dropdowns, it gets in the way.
  $element.attr('autocomplete', 'off');

  const richResultField = new RichResultField(element);

  function displayItem(input) {
    return `${input.name} (${input.value}, ${input.team})`;
  }

  function doSearch(query, callback) {
    currentQuery = query;

    if (currentQuery && currentQuery.trim().length > 0) {
      postJsonWithCredentials(`/service/membersearch/${currentQuery}`, {})
        .then(response => response.json())
        .catch((e) => {
          log.error(e);
          return [];
        })
        .then((response) => {
          // Return the items only if the user hasn't since made a different query
          if (currentQuery === query) {
            callback(response.data.results || []);
          }
        });
    } else {
      callback([]);
    }
  }

  $element.typeahead({
    source: doSearch,
    delay: 200,
    matcher: () => true, // All data received from the server matches the query
    displayText: item => `
        <div class="member-picker-result">
          <div class="media-left">
            ${(item.photo) ? `<img class="media-object" src="${_.escape(item.photo)}" />` : ''}
          </div>
          <div class="media-body">
            <span class="title">${_.escape(item.name)}</span>
            <div class="description">${_.escape(item.team)}</div>
          </div>
        </div>`,
    highlighter: html => html,
    showHintOnFocus: 'all',
    changeInputOnMove: false,
    selectOnBlur: false,
    afterSelect: (item) => {
      if (item) {
        const text = displayItem(item);
        richResultField.store(item.value, _.escape(text));
        $element.data('item', item);
      }
    },
  });

  // On load, look up the existing value and give it human-friendly text if possible
  const currentValue = $element.val();
  if (currentValue && currentValue.trim().length > 0) {
    postJsonWithCredentials(`/service/membersearch/${currentValue}`, {})
      .then(response => response.json())
      .catch((e) => {
        log.error(e);
        return [];
      })
      .then(response => richResultField.storeText(`${_.escape(displayItem(response.data.results[0]))}`));
  }
}

// The jQuery plugin
$.fn.memberPicker = function initMemberPicker() {
  return this.each((i, element) => {
    const $this = $(element);
    if ($this.data('member-picker')) {
      throw new Error('MemberPicker has already been added to this element');
    }

    $this.data('member-picker', new MemberPicker(element));
  });
};

function applyPlugin() {
  $('.member-picker').memberPicker();

  $('.member-picker-collection').each((i, collection) => {
    MultiplePickers(collection, (element) => {
      $(element).memberPicker();
    });
  });
}

/**
 * Any input with the member-picker class will have the picker enabled on it,
 * so you can use the picker without writing any code yourself.
 *
 * More likely you'd use the member-picker tag.
 */
$(applyPlugin);
$('html').on('shown.bs.modal', applyPlugin);

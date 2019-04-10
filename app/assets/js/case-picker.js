import $ from 'jquery';
import log from 'loglevel';
import _ from 'lodash-es';
import 'bootstrap-3-typeahead';
import { postJsonWithCredentials } from '@universityofwarwick/serverpipe';
import RichResultField from './rich-result-field';

export default function CasePicker(element) {
  let currentQuery = null;
  const $element = $(element);
  const team = $element.data('team');
  const member = $element.data('member');
  const state = $element.data('state') || 'All';

  // Might have manually wired this element up with CasePicker,
  // but add the class for CSS style purposes.
  if (!$element.hasClass('case-picker')) {
    $element.addClass('case-picker');
  }

  // Disable browser autocomplete dropdowns, it gets in the way.
  $element.attr('autocomplete', 'off');

  const richResultField = new RichResultField(element);

  function displayItem(input) {
    return `${input.key} ${input.subject}`;
  }

  function doSearch(query, callback) {
    currentQuery = query;

    const requestBody = {
      query,
      team,
      member,
      state,
    };

    postJsonWithCredentials('/service/casesearch', requestBody)
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
  }

  $element.typeahead({
    source: doSearch,
    delay: 200,
    matcher: () => true, // All data received from the server matches the query
    displayText: item => `
        <div class="flexi-picker-result">
          <div class="media-left">
            <i class="fal fa-clipboard-list fa-fw"></i>
          </div>
          <div class="media-body">
            <span class="title">${_.escape(item.key)}</span>
            <span class="type">${_.escape(item.clients)}</span>
            <div class="description">${_.escape(item.subject)}</div>
          </div>
        </div>`,
    highlighter: html => html,
    showHintOnFocus: 'all',
    changeInputOnMove: false,
    selectOnBlur: false,
    afterSelect: (item) => {
      if (item) {
        const text = displayItem(item);
        richResultField.store(item.id, text, item.url);
        $element.data('item', item);
      }
    },
  });

  // On load, look up the existing value and give it human-friendly text if possible
  const currentValue = $element.val();
  if (currentValue && currentValue.trim().length > 0) {
    postJsonWithCredentials(`/service/casesearch/${currentValue}`, {})
      .then(response => response.json())
      .catch((e) => {
        log.error(e);
        return [];
      })
      .then(response => richResultField.storeText(displayItem(response.data.results[0])));
  }
}

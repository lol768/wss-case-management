/* eslint-env browser */
import $ from 'jquery';
import log from 'loglevel';
import 'bootstrap-3-typeahead';
import { postJsonWithCredentials } from './serverpipe';

export default function EnquirySearch(container) {
  let currentQuery = null;
  const $container = $(container);
  const url = $container.prop('action');

  function doSearch(query, callback) {
    currentQuery = query;
    postJsonWithCredentials(url, { query })
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

  const $typeahead = $container.find('input.form-control').typeahead({
    source: (query, callback) => {
      doSearch(query, callback);
    },
    delay: 200,
    matcher: () => true, // All data received from the server matches the query
    displayText: item => `
        <div class="flexi-picker-result">
          <div class="media-left">
            <i class="fal fw ${item.state === 'Closed' ? 'fa-comment-check' : 'fa-comment-dots'} fa-fw"></i>
          </div>
          <div class="media-body">
            <span class="title">${item.key}</span>
            <span class="type">${item.team}</span>
            <div class="description">${item.subject}</div>
          </div>
        </div>`,
    highlighter: html => html,
    changeInputOnSelect: false,
    changeInputOnMove: false,
    followLinkOnSelect: true,
    openLinkInNewTab: true,
    selectOnBlur: false,
    showHintOnFocus: true,
    itemLink: (item) => {
      if (item) {
        return `/team/enquiry/${item.key}`;
      }

      return undefined;
    },
    afterSelect: () => {
      $typeahead.trigger('blur');
    },
  });

  $container.on('submit', (e) => {
    e.preventDefault();
  });
}

/* eslint-env browser */
import $ from 'jquery';
import log from 'loglevel';
import _ from 'lodash-es';
import 'bootstrap-3-typeahead';
import { postJsonWithCredentials } from './serverpipe';
import { formatDateTime } from './dateFormats';

export default function AppointmentSearch(container) {
  let currentQuery = null;
  const $container = $(container);
  const url = $container.prop('action');
  const searchScope = $container.data('searchScope');
  const searchScopeValue = $container.data('searchScopeValue');

  function doSearch(query, callback) {
    currentQuery = query;
    postJsonWithCredentials(url, { query, [searchScope]: searchScopeValue })
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
            <i class="fal fa-clipboard-list fa-fw"></i>
          </div>
          <div class="media-body">
            <span class="title">${_.escape(item.key)}</span>
            <span class="type">${_.escape(item.team)}</span>
            <div class="description">${_.escape(item.subject)}</div>
            <div class="description">${_.escape(formatDateTime(item.start))}</div>
          </div>
        </div>`,
    highlighter: html => html,
    changeInputOnSelect: false,
    changeInputOnMove: false,
    followLinkOnSelect: true,
    selectOnBlur: false,
    showHintOnFocus: true,
    itemLink: (item) => {
      if (item) {
        return `/team/appointment/${_.escape(item.key)}`;
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

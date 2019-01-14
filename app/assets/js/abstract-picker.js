import $ from 'jquery';
import log from 'loglevel';
import _ from 'lodash-es';
import 'bootstrap-3-typeahead';
import { postJsonWithCredentials } from './serverpipe';
import RichResultField from './rich-result-field';

export default class AbstractPicker {
  static displayItem(input) {
    return input.value;
  }

  static transformItem(input) {
    return input;
  }

  static displayText() {
    throw new Error('Abstract method should be overridden');
  }

  constructor(element, classPrefix, url) {
    this.classPrefix = classPrefix;
    this.url = url;
    this.currentQuery = null;
    this.doSearch = this.doSearch.bind(this);
    const $element = $(element);

    // Might have manually wired this element up with a picker,
    // but add the class for CSS style purposes.
    if (!$element.hasClass(`${this.classPrefix}-picker`)) {
      $element.addClass(`${this.classPrefix}-picker`);
    }

    // Disable browser autocomplete dropdowns, it gets in the way.
    $element.attr('autocomplete', 'off');

    const richResultField = new RichResultField(element);

    $element.typeahead({
      source: this.doSearch,
      delay: 200,
      matcher: () => true, // All data received from the server matches the query
      displayText: this.constructor.displayText,
      highlighter: html => html,
      showHintOnFocus: 'all',
      changeInputOnMove: false,
      selectOnBlur: false,
      afterSelect: (item) => {
        if (item) {
          const text = this.constructor.displayItem(item);
          richResultField.store(item.value, text);
          $element.data('item', item);
        }
      },
    });

    // On load, look up the existing value and give it human-friendly text if possible
    const currentValue = $element.val();
    if (currentValue && currentValue.trim().length > 0) {
      postJsonWithCredentials(`${this.url}/${currentValue}`, {})
        .then(response => response.json())
        .catch((e) => {
          log.error(e);
          return [];
        })
        .then((response) => {
          if (response.data && response.data.results) {
            richResultField.storeText(
              this.constructor.displayItem(response.data.results[0]),
            );
          }
        });
    }
  }

  doSearch(query, callback) {
    this.currentQuery = query;

    if (this.currentQuery && this.currentQuery.trim().length > 0) {
      postJsonWithCredentials(`${this.url}/${this.currentQuery}`, {})
        .then(response => response.json())
        .catch((e) => {
          log.error(e);
          return [];
        })
        .then((response) => {
          // Return the items only if the user hasn't since made a different query
          if (this.currentQuery === query) {
            if (response.success) {
              _.forEach(response.data.results, item => this.constructor.transformItem(item));
              callback(response.data.results || []);
            }
          }
        });
    } else {
      callback([]);
    }
  }
}

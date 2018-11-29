import $ from 'jquery';
import log from 'loglevel';
import _ from 'lodash-es';
import 'bootstrap-3-typeahead';
import { postJsonWithCredentials } from './serverpipe';
import RichResultField from './rich-result-field';
import MultiplePickers from './multiple-picker';

/**
 * An AJAX autocomplete-style picker that can return a variety of different
 * result types, such as users or webgroups.
 *
 * The actual searching logic is handled by the corresponding controller -
 * this plugin just passes it option flags to tell it what to search.
 *
 * Requires Bootstrap with the Typeahead plugin.
 */
export default class FlexiPicker {
  constructor(input, {
    includeUsers = true,
    includeGroups = false,
    prefixGroups = '',
    universityId = false,
    potentialClientsOnly = false,
    team = '',
    selectOnBlur = true,
  } = {}) {
    const self = this;
    const $element = $(input);

    // Might have manually wired this element up with FlexiPicker,
    // but add the class for CSS style purposes.
    if (!$element.hasClass('flexi-picker')) {
      $element.addClass('flexi-picker');
    }

    // Disable browser autocomplete dropdowns, it gets in the way.
    $element.attr('autocomplete', 'off');

    this.includeUsers = includeUsers;
    this.includeGroups = includeGroups;
    this.prefixGroups = prefixGroups;
    this.universityId = universityId;
    this.potentialClientsOnly = potentialClientsOnly;
    this.team = team;

    this.richResultField = new RichResultField(input);
    this.currentQuery = null;
    this.doSearch = this.doSearch.bind(this);

    $element.typeahead({
      source: (query, callback) => {
        this.doSearch(query, {}, callback);
      },
      delay: 200,
      matcher: () => true, // All data received from the server matches the query
      displayText: (item) => {
        let icon = '';
        if (item.type === 'user') icon = 'fa-user';
        else if (item.type === 'group') icon = 'fa-globe';

        if (item.photo) {
          return `
            <div class="flexi-picker-result">
              <div class="media-left">
                <img class="media-object" src="${_.escape(item.photo)}" />
              </div>
              <div class="media-body">
                <span class="title">${_.escape(item.title)}</span>
                <div class="description">
                  ${(typeof (item.description) !== 'undefined' ? _.escape(item.description) : '')}
                </div>
              </div>
            </div>`;
        }

        return `
          <div class="flexi-picker-result">
            <i class="fa ${icon}"></i>
            <span class="title">${_.escape(item.title)}</span>
            <span class="type">${_.escape(item.type)}</span>
            <div class="description">
              ${(typeof (item.description) !== 'undefined' ? _.escape(item.description) : '')}
            </div>
          </div>`;
      },
      highlighter: html => html,
      changeInputOnMove: false,
      selectOnBlur,
      afterSelect: (item) => {
        const description = (
          typeof (item.description) !== 'undefined' ? ` (${_.escape(item.description)})` : ''
        );
        const text = `${item.title}${description}`;
        self.richResultField.store(item.value, text);
        $element.data('item', item);
      },
    });

    // On load, look up the existing value and give it human-friendly text if possible
    // NOTE: this relies on the fact that the saved value is itself a valid search term
    // (excluding the prefix on webgroup, which is handled by getQuery() method).
    const currentValue = $element.val();
    if (currentValue && currentValue.trim().length > 0) {
      const searchQuery = (this.prefixGroups && currentValue.indexOf(this.prefixGroups) === 0)
        ? currentValue.substring(this.prefixGroups.length) : currentValue;
      this.doSearch(searchQuery, { exact: true }, (results) => {
        if (results.length > 0) {
          self.richResultField.storeText(`${results[0].title} (${results[0].description})`);
        }
      });
    }
  }

  static transformItem(input) {
    const item = input;

    if (item.type === 'user') {
      item.title = item.name;
      item.description = `${item.value}, ${item.userType}`;

      if (item.department !== null) {
        item.description += `, ${item.department}`;
      }
    } else if (item.type === 'group') {
      item.description = item.title;
      item.title = item.value;
    }
  }

  doSearch(query, options, callback) {
    this.currentQuery = query;

    postJsonWithCredentials('/service/flexipicker', {
      includeUsers: this.includeUsers,
      includeGroups: this.includeGroups,
      universityId: this.universityId,
      potentialClientsOnly: this.potentialClientsOnly,
      team: this.team,
      query,
      exact: options.exact, // if true, only returns 100% matches.
    })
      .then(response => response.json())
      .catch((e) => {
        log.error(e);
        return [];
      })
      .then((response) => {
        // Return the items only if the user hasn't since made a different query
        if (this.currentQuery === query) {
          $.each(response.data.results, (i, item) => FlexiPicker.transformItem(item));
          callback(response.data.results || []);
        }
      });
  }
}

// The jQuery plugin
$.fn.flexiPicker = function initFlexiPicker(options = {}) {
  return this.each((i, element) => {
    const $this = $(element);
    if ($this.data('flexi-picker')) {
      throw new Error('FlexiPicker has already been added to this element');
    }

    const allOptions = {
      includeGroups: $this.data('include-groups'),
      includeUsers: $this.data('include-users') !== false,
      prefixGroups: $this.data('prefix-groups') || '',
      universityId: $this.data('universityid'),
      potentialClientsOnly: $this.data('potential-clients-only'),
      team: $this.data('team') || '',
      selectOnBlur: $this.data('select-on-blur') !== false,
    };
    $.extend(allOptions, options);
    $this.data('flexi-picker', new FlexiPicker(element, allOptions));
  });
};

export function bindTo($scope) {
  $('.flexi-picker', $scope).flexiPicker();

  $('.flexi-picker-collection', $scope).each((i, collection) => {
    MultiplePickers(collection, (element) => {
      $(element).flexiPicker();
    });
  });
}

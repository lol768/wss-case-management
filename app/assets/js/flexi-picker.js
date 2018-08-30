import $ from 'jquery';
import log from 'loglevel';
import 'bootstrap-3-typeahead';
import { postJsonWithCredentials } from './serverpipe';
import RichResultField from './rich-result-field';

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
                <img class="media-object" src="${item.photo}" />
              </div>
              <div class="media-body">
                <span class="title">${item.title}</span>
                <div class="description">
                  ${(typeof (item.description) !== 'undefined' ? item.description : '')}
                </div>
              </div>
            </div>`;
        }

        return `
          <div class="flexi-picker-result">
            <i class="fa ${icon}"></i>
            <span class="title">${item.title}</span>
            <span class="type">${item.type}</span>
            <div class="description">
              ${(typeof (item.description) !== 'undefined' ? item.description : '')}
            </div>
          </div>`;
      },
      highlighter: html => html,
      changeInputOnMove: false,
      afterSelect: (item) => {
        const description = (
          typeof (item.description) !== 'undefined' ? ` (${item.description})` : ''
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
    };
    $.extend(allOptions, options);
    $this.data('flexi-picker', new FlexiPicker(element, allOptions));
  });
};

/**
 * Any input with the flexi-picker class will have the picker enabled on it,
 * so you can use the picker without writing any code yourself.
 *
 * More likely you'd use the flexi-picker tag.
 */
$(() => {
  $('.flexi-picker').flexiPicker();

  /*
  * Handle the multiple-flexi picker, by dynamically expanding to always
  * have at least one empty picker field.
  */
  // Each set of pickers will be in a .flexi-picker-collection
  $('.flexi-picker-collection').each((i, collection) => {
    const $collection = $(collection);
    const $blankInput = $collection
      .find('.flexi-picker-container')
      .first()
      .clone()
      .find('input')
      .val('')
      .end();
    $blankInput.find('a.btn').remove(); // this button is added by initFlexiPicker, so remove it now or we'll see double

    // check whenever field is changed or focused
    if ($collection.data('automatic') === true) {
      $collection.on('change focus', 'input', (ev) => {
        // remove empty pickers
        const $inputs = $collection.find('input');
        if ($inputs.length > 1) {
          $inputs
            .not(':focus')
            .not(':last')
            .filter((j, element) => (element.value || '').trim() === '')
            .closest('.flexi-picker-container')
            .remove();
        }

        // if last picker is nonempty OR focused, append an blank picker.
        const $last = $inputs.last();
        const lastFocused = (ev.type === 'focusin' && ev.target === $last[0]);
        if (lastFocused || $last.val().trim() !== '') {
          const input = $blankInput.clone();
          $collection.append(input);
          input.find('input').first().flexiPicker({});
        }
      });
    } else {
      $collection.append($('<button />')
        .attr({ type: 'button' })
        .addClass('btn').addClass('btn btn-xs btn-default')
        .html('Add another')
        .on('click', () => {
          const input = $blankInput.clone();
          $(this).before(input);
          input.find('input').first().flexiPicker({});
        }));
    }
  });
});

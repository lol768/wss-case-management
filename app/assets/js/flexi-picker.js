import $ from 'jquery';
import log from 'loglevel';
import 'bootstrap-3-typeahead';
import { postJsonWithCredentials } from './serverpipe';

/**
 * RichResultField is a text field that can be overlaid with a similar-looking
 * box containing arbitrary text. Useful if you want to save a particular value
 * from a picker but display something more friendly from the user.
 *
 * Requires Bootstrap.
 */
class RichResultField {
  constructor(input) {
    const self = this;
    this.$input = $(input);
    this.$uneditable = $('<span><span class="val"></span>'
      + '<a href="#" class="clear-field" title="Clear">&times;</a></span>');
    this.$uneditable.attr({
      class: `uneditable-input rich-result-field ${this.$input.attr('class')}`,
      disabled: true,
    });

    this.$input.after(this.$uneditable);
    // Attempt to match the original widths; defined width needed for text-overflow to work
    this.$input.css('width', this.$input.css('width'));
    this.$uneditable.css('width', this.$input.css('width'));
    this.$uneditable.find('a').click(() => {
      self.edit();
      return false;
    });
    this.$uneditable.hide();
  }

  /** Clear field, focus for typing */
  edit() {
    this.$input.val('').typeahead('val', '');
    this.$input.show();
    this.$input.trigger('change').trigger('richResultField.edit').focus();
    this.$uneditable.hide()
      .find('.val')
      .text('')
      .attr('title', '');
  }

  /** Set value of input field, hide it and show the rich `text` instead */
  store(value, text) {
    this.$input.val(value).trigger('change').trigger('richResultField.store');
    this.$input.hide();
    this.$uneditable.show()
      .find('.val')
      .text(text)
      .attr('title', text);
  }
}

/**
 * An AJAX autocomplete-style picker that can return a variety of different
 * result types, such as users, webgroups, and typed-in email addresses.
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
    includeEmail = false,
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
    this.includeEmail = includeEmail;
    this.prefixGroups = prefixGroups;
    this.universityId = universityId;

    this.richResultField = new RichResultField(input);

    let currentQuery = null;

    $element.typeahead({
      source: (query, callback) => {
        currentQuery = query;

        postJsonWithCredentials('/service/flexipicker', {
          includeUsers: this.includeUsers,
          includeGroups: this.includeGroups,
          includeEmail: this.includeEmail,
          universityId: this.universityId,
          query,
        })
          .then(response => response.json())
          .catch((e) => {
            log.error(e);
            return [];
          })
          .then((response) => {
            // Return the items only if the user hasn't since made a different query
            if (currentQuery === query) {
              $.each(response.data.results, (i, item) => FlexiPicker.transformItem(item));
              callback(response.data.results || []);
            }
          });
      },
      delay: 200,
      matcher: () => true, // All data received from the server matches the query
      displayText: (item) => {
        let icon = '';
        if (item.type === 'user') icon = 'fa-user';
        else if (item.type === 'group') icon = 'fa-globe';
        else if (item.type === 'email') icon = 'fa-envelope';

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
      afterSelect: (item) => {
        const description = (
          typeof (item.description) !== 'undefined' ? ` (${item.description})` : ''
        );
        const text = `${item.title}${description}`;
        self.richResultField.store(item.value, text);
        $element.data('item', item);
      },
    });
  }

  static transformItem(input) {
    const item = input;

    if (item.type === 'user') {
      item.title = item.name;

      const userType = (item.isStaff === 'true') ? 'Staff' : 'Student';
      item.description = `${item.value}, ${userType}`;

      if (item.department !== null) {
        item.description += `, ${item.department}`;
      }
    } else if (item.type === 'group') {
      item.description = item.title;
      item.title = item.value;
    } else if (item.type === 'email') {
      item.title = item.name;
      item.description = item.address;
    }
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
      includeEmail: $this.data('include-email'),
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
});

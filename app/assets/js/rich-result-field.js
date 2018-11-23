import $ from 'jquery';

/**
 * RichResultField is a text field that can be overlaid with a similar-looking
 * box containing arbitrary text. Useful if you want to save a particular value
 * from a picker but display something more friendly from the user.
 *
 * Requires Bootstrap.
 */
export default class RichResultField {
  constructor(input) {
    const self = this;
    this.$input = $(input);
    this.$uneditable = $(`
      <span><span class="val"></span>
      <a href="#" class="clear-field" title="Clear">&times;</a></span>
    `);
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
  store(value, text, url) {
    this.$input.val(value).trigger('change').trigger('richResultField.store');
    this.storeText(text, url);
  }

  /** Hide input field and show the rich `text` instead */
  storeText(text, url) {
    this.$input.hide();
    const $val = this.$uneditable.show().find('.val');
    if (url && url.length > 0) {
      $val
        .empty()
        .append($('<a/>').attr({
          target: '_blank',
          href: url,
        }).text(text))
        .attr('title', text);
    } else {
      $val
        .text(text)
        .attr('title', text);
    }
  }
}

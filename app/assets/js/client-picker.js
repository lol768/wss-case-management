import $ from 'jquery';
import 'bootstrap-3-typeahead';
import _ from 'lodash-es/lodash.default';
import MultiplePickers from './multiple-picker';
import AbstractPicker from './abstract-picker';

class ClientPicker extends AbstractPicker {
  static transformItem(input) {
    const item = input;

    item.title = item.name;

    item.description = `${item.value}, ${item.userType}`;

    if (item.department !== null) {
      item.description += `, ${item.department}`;
    }
  }

  static displayItem(input) {
    let description = `${input.value}, ${input.userType}`;
    if (input.department !== null) {
      description += `, ${input.department}`;
    }

    return `${input.name} (${description})`;
  }

  static displayText(item) {
    return `
      <div class="client-search-result">
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
      </div>
    `;
  }

  constructor(element) {
    super(element, 'client', '/service/clientsearch');
  }
}

// The jQuery plugin
$.fn.clientPicker = function initClientPicker() {
  return this.each((i, element) => {
    const $this = $(element);
    if ($this.data('client-picker')) {
      throw new Error('ClientPicker has already been added to this element');
    }

    $this.data('client-picker', new ClientPicker(element));
  });
};

export default function bindTo($scope) {
  $('.client-picker', $scope).clientPicker();

  $('.client-picker-collection', $scope).each((i, collection) => {
    MultiplePickers(collection, (element) => {
      $(element).clientPicker();
    });
  });
}

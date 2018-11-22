import $ from 'jquery';
import 'bootstrap-3-typeahead';
import _ from 'lodash-es';
import MultiplePickers from './multiple-picker';
import AbstractPicker from './abstract-picker';

class MemberPicker extends AbstractPicker {
  static displayItem(input) {
    return `${input.name} (${input.value}, ${input.team})`;
  }

  static displayText(item) {
    return `
      <div class="member-picker-result">
        <div class="media-left">
          ${(item.photo) ? `<img class="media-object" src="${_.escape(item.photo)}" />` : ''}
        </div>
        <div class="media-body">
          <span class="title">${_.escape(item.name)}</span>
          <div class="description">${_.escape(item.team)}</div>
        </div>
      </div>
    `;
  }

  constructor(element) {
    super(element, 'member', '/service/membersearch');
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

export default function bindTo($scope) {
  $('.member-picker', $scope).memberPicker();

  $('.member-picker-collection', $scope).each((i, collection) => {
    MultiplePickers(collection, (element) => {
      $(element).memberPicker();
    });
  });
}

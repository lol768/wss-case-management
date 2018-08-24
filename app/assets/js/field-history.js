/* eslint-env browser */

import _ from 'lodash-es';
import $ from 'jquery';
import * as DateFormats from './dateFormats';

function getValue(value) {
  if (_.isArray(value)) {
    return _.map(value, v => v.description || v).join(', ');
  }
  return value.description || value;
}

export default function FieldHistory(container) {
  const $container = $(container);
  $.get($container.data('href')).done((data) => {
    $container.find('[data-field-history]').each((j, term) => {
      const $term = $(term);
      const $field = $term.data('field-history');
      if ($field && data[$field] && data[$field].length > 1) {
        $term.append($('<i/>').addClass('fa fa-history').prop({
          title: 'View history',
        }).data('field-history-data', data[$field]));
      }
    });
    $container.on('click', '.fa-history', (e) => {
      const $i = $(e.target);
      $i.popover({
        container: 'body',
        content: _.map($i.data('field-history-data'), item => `
            <dl>
              <dt>${DateFormats.formatDateTime(item.version)}</dt>
              <dd>${getValue(item.value)}</dd>
            </dl>
          `),
        template: `
            <div class="popover" role="tooltip">
              <div class="arrow"></div>
              <button type="button" class="close" aria-label="Close"><span aria-hidden="true">×</span></button>
              <div class="popover-content"></div>
            </div>`,
        html: true,
        trigger: 'manual',
        selector: true,
      }).popover('show');
    });
  });
}

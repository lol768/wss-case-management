/* eslint-env browser */

import _ from 'lodash-es';
import $ from 'jquery';
import * as DateFormats from './dateFormats';

function getValue(value) {
  if (_.isArray(value)) {
    return _.map(value, v => v.description || v.name || v).join(', ');
  }
  return value.description || value.name || value;
}

function handleData($container, data) {
  $container.find('[data-field-history-field]').each((j, term) => {
    const $term = $(term);
    const $field = $term.data('field-history-field');
    if ($field && data[$field] && data[$field].length > 1) {
      const $target = ($term.data('field-history-target')) ? $($term.data('field-history-target')) : $term;
      $target.append($('<i/>').addClass('fa fa-history has-popover has-field-history').prop({
        title: 'View history',
      }).data('field-history-data', data[$field]));
    }
  });

  $('.fa-history.has-popover.has-field-history').on('click', (e) => {
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
      placement: $i.parent().hasClass('pull-right') ? 'left' : 'right',
    }).popover('show');
  });
}

export default function FieldHistory(container) {
  const $container = $(container);
  if ($container.data('href')) {
    $.get($container.data('href')).done(data => handleData($container, data));
  } else if ($container.data('field-history')) {
    handleData($container, $container.data('field-history'));
  }
}

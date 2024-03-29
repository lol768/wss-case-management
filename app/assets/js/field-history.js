/* eslint-env browser */

import _ from 'lodash-es';
import $ from 'jquery';
import * as DateFormats from './dateFormats';

function getValue(value) {
  if (_.isArray(value)) {
    if (value.length === 0) {
      return '(none)';
    }
    return _.map(value, v => v.description || v.name || v).join(', ');
  }
  return _.get(value, 'description', undefined) || _.get(value, 'name', undefined) || value || 'None';
}

function handleData($container, data) {
  const bindEvents = ($el) => {
    $el.on('click', (e) => {
      const $i = $(e.target);
      $i.popover({
        container: 'body',
        content: _.map($i.data('field-history-data'), item => `
            <dl>
              <dt>${DateFormats.formatDateTime(item.version)}</dt>
              ${(item.user) ? `<dt>${_.escape(item.user)}</dt>` : ''}
              <dd>${_.escape(getValue(item.value))}</dd>
            </dl>
          `),
        template: `
            <div class="popover" role="tooltip">
              <div class="arrow"></div>
              <button type="button" class="close" aria-label="Close"><span aria-hidden="true">×</span></button>
              <div class="popover-content"></div>
            </div>`,
        html: true,
        sanitize: false,
        trigger: 'manual',
        selector: true,
        placement: $i.parent().hasClass('pull-right') ? 'left' : 'right',
      }).popover('show');
    });
  };

  $container.find('[data-field-history-field]').each((j, term) => {
    const $term = $(term);
    const $field = $term.data('field-history-field');
    if ($field && data[$field] && data[$field].length > 1) {
      const $target = ($term.data('field-history-target')) ? $($term.data('field-history-target')) : $term;
      const $el = $('<i/>').addClass('fa fa-history has-popover has-field-history').prop({
        title: 'View history',
      }).data('field-history-data', data[$field]);
      $target.append($el);

      if ($container.has($el[0]).length === 0) {
        // Outside of $container so needs binding separately
        bindEvents($el);
      }
    }
  });

  $container.find('.fa-history.has-popover.has-field-history').each((i, element) => bindEvents($(element)));
}

export default function FieldHistory(container) {
  const $container = $(container);
  if ($container.data('href')) {
    $.get($container.data('href')).done(data => handleData($container, data));
  } else if ($container.data('field-history')) {
    handleData($container, $container.data('field-history'));
  }
}

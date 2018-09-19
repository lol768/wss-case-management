import $ from 'jquery';
import _ from 'lodash-es';
import log from 'loglevel';
import { postJsonWithCredentials, postMultipartFormWithCredentials } from './serverpipe';

export default function MessageThreads(container) {
  const $container = $(container);

  // Scroll to bottom when expanded
  $container.find('.collapse').on('shown.bs.collapse', (e) => {
    const $target = $(e.target);
    $target.find('.panel-body').scrollTop(Number.MAX_SAFE_INTEGER);

    // POST to the ping endpoint specified if it exists
    if ($target.data('ping')) {
      postJsonWithCredentials($target.data('ping'), {})
        .then(response => response.json())
        .catch(err => log.error(err));
    }
  });
  // And on load
  $('.collapse.in .panel-body').scrollTop(Number.MAX_SAFE_INTEGER);

  function checkAndUpdateSendButton() {
    const $textarea = $('.collapse.in .panel-footer textarea');
    const $button = $('.collapse.in .panel-footer button[type=submit]');
    if (_.trim($textarea.val()).length === 0) {
      $button.prop('disabled', true);
    } else {
      $button.prop('disabled', false);
    }
  }

  $container.on('keydown', 'textarea', (e) => {
    const $target = $(e.target);
    // Grow textarea when typing
    // For some reason carriage returns don't update the height until the methods returns,
    // so just wait a tick before checking the height
    setTimeout(() => {
      if ($target.get(0).scrollHeight > $target.get(0).offsetHeight && $target.prop('rows') < 5) {
        $target.prop('rows', $target.prop('rows') + 1);
      }
    }, 1);
  });

  $container.on('keyup', checkAndUpdateSendButton);
  checkAndUpdateSendButton();

  $container.on('submit', (e) => {
    e.preventDefault();
    const $form = $(e.target);
    $form.find(':input').prop('readonly', true);
    $form.find('button').prop('disabled', true);
    $form.find('.alert-danger').empty().addClass('hidden');
    postMultipartFormWithCredentials($form.prop('action'), e.target)
      .then(response => response.json())
      .then((response) => {
        if (response.success) {
          $form.closest('.panel').find('.panel-body').append($('<div/>').html(response.data.message).unwrap());
          $('.collapse.in .panel-body').scrollTop(Number.MAX_SAFE_INTEGER);
          $form.trigger('reset');
        } else {
          log.error(response);
          if (response.errors && response.errors.length) {
            $form.find('.alert-danger').empty().html(_.map(response.errors, error => error.message).join(', ')).removeClass('hidden');
          } else {
            $form.find('.alert-danger').empty().html('An unknown error occurred').removeClass('hidden');
          }
        }
        $form.find(':input').prop('readonly', false);
        $form.find('button').prop('disabled', false);
      })
      .catch((error) => {
        log.error(error);
        $form.find('.alert-danger').empty().html(error.message).removeClass('hidden');
        $form.find(':input').prop('readonly', false);
        $form.find('button').prop('disabled', false);
      })
      .finally(() => {
        checkAndUpdateSendButton();
      });
  });
}

import $ from 'jquery';
import _ from 'lodash-es';
import log from 'loglevel';
import { fetchWithCredentials, postJsonWithCredentials, postMultipartFormWithCredentials } from './serverpipe';

function updateAwaitingIcon($form, awaiting) {
  // Look for icon in panel group
  let $iconContainer = $form.closest('.panel').find('.panel-heading .panel-title');
  if ($iconContainer.length === 0) {
    // Look for icon in single enquiry view for client
    $iconContainer = $form.closest('.id7-main-content-area').find('h2 .icon-container');
  }
  if ($iconContainer.length === 0) {
    // Look for icon in single enquiry view for team member
    $iconContainer = $form.closest('.id7-main-content-area').find('h1');
  }
  if ($iconContainer.length > 0) {
    const $awaitingIcon = $iconContainer.find('i.awaiting');
    const $otherIcon = $awaitingIcon.next('i');
    if (awaiting) {
      $awaitingIcon.removeClass('hidden');
      $otherIcon.addClass('hidden');
    } else {
      $awaitingIcon.addClass('hidden');
      $otherIcon.removeClass('hidden');
    }
  }
}

const updateFrequencyInMillis = 1000 * 60; // 60 seconds

function updateThread($thread) {
  const url = $thread.data('href');
  const $form = $thread.find('form');
  const shouldUpdate = $thread.find('.collapsed').not('[data-always-update="true"]').length > 0;
  if (!$thread.data('sending') && !shouldUpdate) {
    fetchWithCredentials(url)
      .then(response => response.json())
      .then((response) => {
        if (response.success) {
          $thread.find('.panel-body').html(response.data.messagesHTML);
          $('.collapse.in .panel-body').scrollTop(Number.MAX_SAFE_INTEGER);
          $form.find('input[name="lastMessage"]').val(response.data.lastMessage);
          if (typeof response.data.awaiting !== 'undefined') {
            updateAwaitingIcon($form, response.data.awaiting);
          }
          if (typeof response.data.lastMessageRelative !== 'undefined') {
            $thread.find('.lastMessageRelative').html(response.data.lastMessageRelative);
          }
          if (typeof response.data.threadTitle !== 'undefined') {
            $thread.find('.threadTitle').html(response.data.threadTitle);
          }
        } else {
          log.error(response);
        }
      })
      .catch((error) => {
        log.error(error);
      })
      .finally(() => {
        setTimeout(() => {
          updateThread($thread);
        }, updateFrequencyInMillis);
      });
  } else {
    setTimeout(() => {
      updateThread($thread);
    }, updateFrequencyInMillis);
  }
}

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

  function checkAndUpdateSendButton(e) {
    const $thread = (e !== undefined) ? $(e.target).closest('.thread') : $container;
    const $textarea = $thread.find('.panel-footer textarea');
    const $button = $thread.find('.panel-footer button[type=submit]');
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
    const $thread = $form.closest('.thread').data('sending', true);
    $form.find(':input').prop('readonly', true);
    $form.find('button').prop('disabled', true);
    $form.find('.error').empty().addClass('hidden');
    $form.find('.needs-refresh').addClass('hidden');
    postMultipartFormWithCredentials($form.prop('action'), e.target)
      .then(response => response.json())
      .then((response) => {
        if (response.success) {
          $form.closest('.panel').find('.panel-body').append(response.data.message);
          $('.collapse.in .panel-body').scrollTop(Number.MAX_SAFE_INTEGER);
          $form.trigger('reset');
          $form.find('textarea').prop('rows', 1);
          $form.find('input[name="lastMessage"]').val(response.data.lastMessage);
          updateAwaitingIcon($form, false);
          if (typeof response.data.lastMessageRelative !== 'undefined') {
            $thread.find('.lastMessageRelative').html(response.data.lastMessageRelative);
          }
          if (typeof response.data.threadTitle !== 'undefined') {
            $thread.find('.threadTitle').html(response.data.threadTitle);
          }
        } else if (response.errors && response.errors.length) {
          if (response.errors[0].messageKey === 'error.optimisticLocking') {
            $form.find('.needs-refresh').removeClass('hidden');
          } else {
            $form.find('.error').empty().html(_.map(response.errors, error => error.message).join(', ')).removeClass('hidden');
          }
        } else {
          log.error(response);
          $form.find('.error').empty().html('An unknown error occurred').removeClass('hidden');
        }
        $form.find(':input').prop('readonly', false);
        $form.find('button').prop('disabled', false);
      })
      .catch((error) => {
        log.error(error);
        $form.find('.error').empty().html(error.message).removeClass('hidden');
        $form.find(':input').prop('readonly', false);
        $form.find('button').prop('disabled', false);
      })
      .finally(() => {
        checkAndUpdateSendButton(e);
        $thread.data('sending', false);
      });
  });

  $container.find('.needs-refresh button').on('click', (e) => {
    const $button = $(e.target);
    $button.closest('div.alert').addClass('hidden');
    updateThread($button.closest('.thread'));
  });

  // Periodically update threads
  $container.find('.thread').each((i, thread) => {
    setTimeout(() => {
      updateThread($(thread));
    }, updateFrequencyInMillis);
  });
}

import $ from 'jquery';
import _ from 'lodash-es/lodash.default';

export default function UserListPopovers() {
  $('.user-list-popover').each((i, button) => {
    const $button = $(button);
    $button.on('click', () => {
      let content = '';
      if ($button.data('users').length > 0) {
        const sorted = _.sortBy($button.data('users'), 'name');
        content += `<ul class="list-unstyled">
          ${_.map(sorted, u => `
            <li>${u.name} (${u.universityID})</li>
          `).join('')}
        </ul>`;
      }
      if ($button.data('moreLink')) {
        content += `
          <a href="${$button.data('moreLink')}">${$button.data('moreCaption')}</a>
        `;
      }
      $button.popover({
        container: 'body',
        content,
        template: `
            <div class="popover" role="tooltip">
              <div class="arrow"></div>
              <button type="button" class="close" aria-label="Close"><span aria-hidden="true">×</span></button>
              <div class="popover-content"></div>
            </div>`,
        html: true,
        trigger: 'manual',
        selector: true,
      }).addClass('has-popover').popover('show');
    });
  });
}

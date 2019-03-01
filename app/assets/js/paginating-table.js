import $ from 'jquery';
import { fetchWithCredentials } from '@universityofwarwick/serverpipe';
import log from 'loglevel';

export default function PaginatingTable(element, bindTo) {
  const $table = $(element);

  $table.on('click', '.pagination-link', (event) => {
    const $pagination = $(event.target);
    const $existingPagination = $pagination.closest('tr');
    $existingPagination.find('td').html('<i class="fas fa-spinner fa-pulse"></i> Loading&hellip;');

    fetchWithCredentials($pagination.attr('href'))
      .then((response) => {
        if (response.status === 200) {
          return response.text();
        }
        throw new Error(response.statusText || response.status || 'error');
      }).then((html) => {
        const $newRows = $(html);
        const $newPagination = $newRows.filter('.pagination-row').detach();
        $table.find('tbody').append($newRows);
        $table.find('tfoot').append($newPagination);
        $existingPagination.remove();
      }).catch((err) => {
        $pagination.closest('td').text(`Unable to load content: ${err.message || 'error'}`);
      });

    event.preventDefault();
  });

  // Filters
  $table.find('> thead [data-toggle="filter"]').each((i, btn) => {
    const $btn = $(btn);

    if ($btn.hasClass('has-popover')) return;

    const $filter = $btn.next('.filter');

    if ($filter.length === 1) {
      $btn.popover({
        trigger: 'manual',
        container: 'body',
        template: '<div class="popover filter"><div class="arrow"></div><div class="popover-inner"><div class="popover-content"><p></p></div></div></div>',
        html: true,
        sanitize: false,
        content: $filter.html(),
        placement: 'bottom',
      }).addClass('has-popover');

      $filter.remove();

      $btn.on('click', () => $btn.popover('toggle'));
      $btn.on('shown.bs.popover', () => {
        const $popover = $btn.data('bs.popover').tip();
        $popover.find('button[type="submit"]').on('click', () => {
          let href = $table.data('pagination');
          const values = $popover.find(':input').serializeArray()
            .map(v => `${encodeURIComponent(v.name)}=${encodeURIComponent(v.value)}`)
            .join('&');

          if (values) {
            if (href.indexOf('?') !== -1) {
              href += `&${values}`;
            } else {
              href += `?${values}`;
            }
          }

          fetchWithCredentials(href)
            .then((response) => {
              if (response.status === 200) {
                return response.text();
              }
              throw new Error(response.statusText || response.status || 'error');
            }).then((html) => {
              $btn.popover('hide');
              const $parent = $table.parent();
              $table.replaceWith(html);
              bindTo($parent);
            }).catch((err) => {
              log.error(err);
            });
        });
      });
    } else {
      $btn.remove();
    }
  });
}

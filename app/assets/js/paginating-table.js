import $ from 'jquery';

export default function PaginatingTable(element) {
  const $table = $(element);

  $table.on('click', '.pagination-link', (e) => {
    const $pagination = $(e.target);
    const $existingPagination = $pagination.closest('tr');
    $existingPagination.find('td').html('<i class="fas fa-spinner fa-pulse"></i> Loading&hellip;');
    $.get($pagination.attr('href'), (data, status, xhr) => {
      if (status === 'error') {
        $pagination.closest('td').text(`Unable to load content: ${xhr.statusText || xhr.status || 'error'}`);
      } else {
        const $newRows = $(data);
        const $newPagination = $newRows.filter('.pagination-row').detach();
        $table.find('tbody').append($newRows);
        $table.find('tfoot').append($newPagination);
        $existingPagination.remove();
      }
    });
    e.preventDefault();
  });
}

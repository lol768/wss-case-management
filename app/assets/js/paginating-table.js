import $ from 'jquery';

export default function PaginatingTable(element) {
  const $table = $(element);

  $table.on('click', '.pagination-link', (e) => {
    const $pagination = $(e.target);
    const $row = $pagination.closest('tr');
    $row.html('<i class="fas fa-spinner fa-pulse"></i> loading&hellip;');
    $.get($pagination.attr('href'), (data, status, xhr) => {
      if (status === 'error') {
        $pagination.closest('td').text(`Unable to load content: ${xhr.statusText || xhr.status || 'error'}`);
      } else {
        $table.find('tbody').append(data);
        $row.remove();
      }
    });
    e.preventDefault();
  });
}

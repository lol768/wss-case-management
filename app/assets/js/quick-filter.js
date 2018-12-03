import $ from 'jquery';

export default function QuickFilter(container) {
  const $container = $(container);
  const targetClassPrefix = $container.data('target-class-prefix');
  const $checkboxes = $container.find('input:checkbox');
  const $targetContainer = $($container.data('target'));
  const $items = $targetContainer.children();

  $checkboxes.on('change', () => {
    const types = $checkboxes.filter(':checked').map((i, el) => el.value);
    const showAll = types.length === 0 || types.length >= $checkboxes.length;

    if (showAll) {
      $items.show();
    } else {
      $items.hide();
      types.each((i, t) => {
        const selector = `.${targetClassPrefix}${t}`;
        $items.filter(selector).show();
      });
    }
  });
}

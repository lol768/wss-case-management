import $ from 'jquery';

export default function MultiplePickers(collection, callback) {
  const $collection = $(collection);
  const containerClass = $collection.data('containers') || 'flexi-picker-container';
  const $blankInput = $collection
    .find(`.${containerClass}`)
    .first()
    .clone()
    .find('input')
    .val('')
    .end();

  $blankInput.find('a.btn').remove(); // remove buttons that will be added later by flexi-picker

  // check whenever field is changed or focused
  if ($collection.data('automatic') === true) {
    $collection.on('change focus', 'input', (ev) => {
      // remove empty pickers
      const $inputs = $collection.find('input');
      if ($inputs.length > 1) {
        $inputs
          .not(':focus')
          .not(':last')
          .filter((j, element) => (element.value || '').trim() === '')
          .closest(`.${containerClass}`)
          .remove();
      }

      // if last picker is nonempty OR focused, append an blank picker.
      const $last = $inputs.last();
      const lastFocused = (ev.type === 'focusin' && ev.target === $last[0]);
      if (lastFocused || $last.val().trim() !== '') {
        const input = $blankInput.clone();
        $collection.append(input);
        callback(input.find('input').first()[0]);
      }
    });
  } else {
    $collection.append($('<button />')
      .attr({ type: 'button' })
      .addClass('btn').addClass('btn btn-xs btn-default')
      .html('Add another')
      .on('click', () => {
        const input = $blankInput.clone();
        $(this).before(input);
        callback(input.find('input').first()[0]);
      }));
  }
}

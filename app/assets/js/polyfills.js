/* eslint-env browser */
import 'core-js/features/object/assign';
import 'core-js/features/promise';
import 'core-js/features/dom-collections';
import 'core-js/features/string/ends-with';
import 'core-js/features/array/iterator';
import 'whatwg-fetch';

const polyfillClosest = () => {
  const ElementPrototype = window.Element.prototype;

  if (typeof ElementPrototype.matches !== 'function') {
    ElementPrototype.matches = ElementPrototype.msMatchesSelector
      || ElementPrototype.mozMatchesSelector
      || ElementPrototype.webkitMatchesSelector
      || function matches(selector) {
        const element = this;
        const elements = (element.document || element.ownerDocument).querySelectorAll(selector);
        let index = 0;

        while (elements[index] && elements[index] !== element) {
          index += 1;
        }

        return Boolean(elements[index]);
      };
  }

  if (typeof ElementPrototype.closest !== 'function') {
    ElementPrototype.closest = function closest(selector) {
      let element = this;

      while (element && element.nodeType === 1) {
        if (element.matches(selector)) {
          return element;
        }

        element = element.parentNode;
      }

      return null;
    };
  }
};

polyfillClosest();

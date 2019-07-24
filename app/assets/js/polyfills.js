/* eslint-env browser */
import 'core-js/modules/es.object.assign';
import 'core-js/modules/es.promise';
import 'core-js/modules/es.promise.finally';
import 'core-js/modules/es.string.ends-with';
import 'core-js/modules/es.array.iterator';
import 'core-js/modules/web.dom-collections.for-each';

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

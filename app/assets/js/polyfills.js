/* eslint-env browser */
import 'core-js/modules/es.object.assign';
import 'core-js/modules/es.promise';
import 'core-js/modules/es.promise.finally';
import 'core-js/modules/es.string.ends-with';
import 'core-js/modules/es.array.iterator';
import polyfillClosest from 'element-closest';

polyfillClosest(window);

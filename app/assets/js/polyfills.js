/* eslint-env browser */
import 'core-js/modules/es6.object.assign';
import 'core-js/modules/es6.promise';
import 'core-js/modules/es7.promise.finally';
import 'core-js/modules/es6.string.ends-with';
import 'core-js/modules/es6.array.iterator';
import polyfillClosest from 'element-closest';

polyfillClosest(window);

import chai from 'chai';
import jsdom from 'jsdom';

global.expect = chai.expect;
global.should = chai.should();
global.assert = chai.assert;
global.sinon = require('sinon');

// chai assertion plugins
chai.use(require('sinon-chai'));

const dom = new jsdom.JSDOM();
global.window = dom.window;
global.document = dom.window.document;
global.navigator = dom.window.navigator;

// Do helpful things with Spies.  Use inside a test suite `describe` block.
global.spy = function spy(object, method) {
  // Spy on the method before any tests run
  before(() => {
    sinon.spy(object, method);
  });

  // Re-initialise the spy before each test
  beforeEach(() => {
    object[method].reset();
  });

  // Restore the original method after all tests have run
  after(() => {
    object[method].restore();
  });
};

/* global Modernizr:false */
// https://github.com/Modernizr/Modernizr/blob/master/feature-detects/img/webp.js

(function addTest() {
  const webpTests = [{
    uri: 'data:image/webp;base64,UklGRiQAAABXRUJQVlA4IBgAAAAwAQCdASoBAAEAAwA0JaQAA3AA/vuUAAA=',
    name: 'webp',
  }, {
    uri: 'data:image/webp;base64,UklGRkoAAABXRUJQVlA4WAoAAAAQAAAAAAAAAAAAQUxQSAwAAAABBxAR/Q9ERP8DAABWUDggGAAAADABAJ0BKgEAAQADADQlpAADcAD++/1QAA==',
    name: 'webp.alpha',
  }, {
    uri: 'data:image/webp;base64,UklGRlIAAABXRUJQVlA4WAoAAAASAAAAAAAAAAAAQU5JTQYAAAD/////AABBTk1GJgAAAAAAAAAAAAAAAAAAAGQAAABWUDhMDQAAAC8AAAAQBxAREYiI/gcA',
    name: 'webp.animation',
  }, {
    uri: 'data:image/webp;base64,UklGRh4AAABXRUJQVlA4TBEAAAAvAAAAAAfQ//73v/+BiOh/AAA=',
    name: 'webp.lossless',
  }];

  function test(name, uri, cb) {
    const image = new Image();

    function addResult(event) {
      // if the event is from 'onload', check the see if the image's width is
      // 1 pixel (which indicates support). otherwise, it fails

      const result = event && event.type === 'load' ? image.width === 1 : false;
      const baseTest = name === 'webp';

      // if it is the base test, and the result is false, just set a literal false
      // rather than use the Boolean constructor
      // eslint-disable-next-line no-new-wrappers
      Modernizr.addTest(name, (baseTest && result) ? new Boolean(result) : result);

      if (cb) {
        cb(event);
      }
    }

    image.onerror = addResult;
    image.onload = addResult;

    image.src = uri;
  }

  // test for webp support in general
  const webp = webpTests.shift();
  test(webp.name, webp.uri, (e) => {
    // if the webp test loaded, test everything else.
    if (e && e.type === 'load') {
      webpTests.forEach(webpTest => test(webpTest.name, webpTest.uri));
    }
  });
}());

'use strict';
/*
 * Build file for JS and CSS assets.
 *
 *     npm install
 *     node_modules/.bin/gulp watch-assets
 */
const gulp = require('gulp');
const fs = require('fs');
const gutil = require('gulp-util');

/* Recommended gulpopts.json:

 {
  "env": {
   "PRODUCTION": false
  }
 }

 */
let gulpOpts = { env: {} };
try {
  fs.accessSync('./gulpopts.json', fs.R_OK);
  gulpOpts = require('./gulpopts.json');
  gutil.log('Got opts');
} catch (e) {
  gutil.log(gutil.colors.yellow('No gulpopts.json ('+e.message+')'));
}
function option(name, fallback) {
  const value = process.env[name] || gulpOpts.env[name];
  if (value === undefined) return fallback;
  return (value === 'true' || value === true);
}

// Some naughty globals

global.paths = {
  assetPath: 'public',
  scriptOut: 'target/gulp/js',
  assetsOut: 'target/gulp',
  styleIn: [
    'public/css/main.less',
  ],
  styleOut: 'target/gulp/css',
  // Paths under node_modules that will be searched when @import-ing in your LESS.
  styleModules: [
    'id7/less',
  ],
};

global.PRODUCTION = option('PRODUCTION', true);
global.UGLIFY = option('UGLIFY', PRODUCTION);
if (PRODUCTION) {
  gutil.log(gutil.colors.yellow('Production build.'));
} else {
  gutil.log(gutil.colors.yellow('Development build.'));
}
gutil.log("Uglify: " + UGLIFY);

require('./gulp/scripts');
require('./gulp/styles');

gulp.task('all-static', ['id7-static','scripts', 'styles']);

// Shortcuts for building all asset types at once
gulp.task('assets', ['lint', 'all-static']);
gulp.task('watch-assets', ['watch-scripts', 'watch-styles']);
gulp.task('wizard', ['watch-assets']);

gulp.task('default', ['wizard']);
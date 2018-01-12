'use strict';

const gulp = require('gulp');
const fs = require('fs');
const gutil = require('gulp-util');
const sourcemaps = require('gulp-sourcemaps');
const source = require('vinyl-source-stream');
const buffer = require('vinyl-buffer');
const _ = require('lodash');
const path = require('path');
const mold = require('mold-source-map');

const babelify = require('babelify');
const browserify = require('browserify');
const browserifyInc = require('browserify-incremental');
const browserifyShim = require('browserify-shim');
const envify = require('loose-envify/custom');
const eslint = require('gulp-eslint');
const uglify = require('gulp-uglify');
const watchify = require('watchify');
const merge = require('merge-stream');

const bundleEvents = require('./events');

function browserifyOptions(cacheName, entries) {
  return {
    entries: entries,
    basedir: 'public/js',
    cacheFile: `./target/browserify-cache-${cacheName}.json`,
    debug: true, // confusingly, this enables sourcemaps
    transform: [
      babelify,
      [
        envify({ _: 'purge', NODE_ENV: PRODUCTION ? 'production' : 'development' }),
        { global: true },
      ],
      [browserifyShim, { global: true }],
    ],
    // Excluded from bundle, then browserify-shim hooks up require('jquery') to point
    // at the global version.
    excludes: ['jquery'],
  };
}

function createBrowserify(options) {
  var factory = browserifyInc;
  if (options.incremental === false) {
    factory = browserify;
  }
  const b = factory(options);
  (options.excludes || []).forEach((el) => b.exclude(el));
  return b;
}

// Function for running Browserify on JS, since
// we reuse it a couple of times.
function bundle(b, outputFile) {
  return b.bundle()
    .on('error', (e) => {
      gutil.log(gutil.colors.red(e.stack));
    })
    .pipe(mold.transformSourcesRelativeTo(path.join(__dirname, '..')))
    .pipe(source(outputFile))
    .pipe(buffer())
    .pipe(sourcemaps.init({ loadMaps: true }))
      .pipe(UGLIFY ? uglify() : gutil.noop())
    .pipe(sourcemaps.write('.'))
    .pipe(gulp.dest(paths.scriptOut));
}

const SCRIPTS = {
  // Mapping from bundle name to entry point
  'bundle.js': 'main.js',
};

gulp.task('scripts', [], () =>
  merge(_.map(SCRIPTS, (entries, output) => {
    const bopts = browserifyOptions(cacheName(output), entries);
    const b = createBrowserify(bopts);
    return bundle(b, output);
  }))
);

function cacheName(name) {
  return name.replace('.js', '');
}

// Recompile scripts on changes. Watchify is more efficient than
// grunt.watch as it knows how to do incremental rebuilds.
gulp.task('watch-scripts', [], () =>
  merge(_.map(SCRIPTS, (entries, output) => {
    // incremental doesn't play nice with watchify, so disable
    const bopts = _.extend({ incremental: false }, watchify.args, browserifyOptions(cacheName(output), entries));
    const b = watchify(createBrowserify(bopts));
    b.on('update', () => {
      bundleEvents.emit('scripts-updating');
      bundle(b, output).on('end', () => {
        bundleEvents.emit('scripts-updated');
      });
    });
    b.on('log', gutil.log);
    return bundle(b, output);
  }))
);

gulp.task('lint', () => {
  return gulp.src([`${paths.assetPath}/js/**/*.js`])
    .pipe(eslint('.eslintrc.json'))
    .pipe(eslint.format())
    .pipe(eslint.failAfterError());
});
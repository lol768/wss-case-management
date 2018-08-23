import path from 'path';
import EventEmitter from 'events';
import WebpackNotifierPlugin from 'webpack-notifier';
import RemovePlugin from 'remove-files-webpack-plugin';

import PlayFingerprintsPlugin from './build-tooling/PlayFingerprintsPlugin';
import WatchEventsPlugin from './build-tooling/WatchEventsPlugin';

const merge = require('webpack-merge');
const tooling = require('./build-tooling/webpack.tooling');

const paths = {
  ROOT: __dirname,
  RELATIVE_ASSETS: 'target/assets',
  ASSETS: path.join(__dirname, 'target/assets'),
  NODE_MODULES: path.join(__dirname, 'node_modules/id7'),
  ID7: path.join(__dirname, 'node_modules/id7'),
  ENTRY: './app/assets/js/main.js',
  PUBLIC_PATH: '/assets',
};

const commonConfig = merge([
  {
    output: {
      path: paths.ASSETS,
      publicPath: paths.PUBLIC_PATH,
    },
    plugins: [
      new PlayFingerprintsPlugin(),
      new RemovePlugin({
        before: {
          root: paths.ROOT,
          include: [paths.RELATIVE_ASSETS],
        },
        after: {
          root: paths.ROOT,
          test: [
            {
              folder: paths.RELATIVE_ASSETS,
              method: filePath => (new RegExp(/style\.js.*$/, 'm').test(filePath)),
            },
          ],
        },
      }),
    ],
    resolve: {
      alias: {
        id7: paths.ID7,
      },
    },
    externals: {
      jquery: 'jQuery'
    }
  },
  tooling.lintJS(),
  tooling.transpileJS({
    entry: {
      main: paths.ENTRY,
    },
  }),
  tooling.copyNpmDistAssets({
    dest: path.join(paths.ASSETS, 'lib'),
    modules: ['id7'],
  }),
  tooling.copyLocalImages({
    dest: paths.ASSETS,
  }),
  tooling.extractCSS({
    resolverPaths: [
      paths.NODE_MODULES,
    ],
  }),
]);

const productionConfig = merge([
  {
    mode: 'production',
    optimization: {
      splitChunks: {
        chunks: 'initial',
      },
      runtimeChunk: {
        name: 'manifest',
      },
    },
    performance: {
      hints: 'error',
    },
  },
  tooling.transpileJS(),
  tooling.minify(),
  tooling.generateSourceMaps('hidden-source-map'),
]);

const developmentConfig = merge([
  {
    mode: 'development',
    devtool: 'inline-cheap-source-map',
    plugins: [
      new WebpackNotifierPlugin(),
      new WatchEventsPlugin({ emitter: new EventEmitter() }),
    ],
  },
  tooling.generateSourceMaps('source-map'),
]);

module.exports = (env) => {
  let config = merge(commonConfig, developmentConfig, { mode: env });


  if (env === 'production') {
    config = merge(commonConfig, productionConfig, { mode: env });
  }

  return config;
};

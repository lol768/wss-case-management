import CopyWebpackPlugin from 'copy-webpack-plugin';
import TerserPlugin from 'terser-webpack-plugin';
import MiniCssExtractPlugin from 'mini-css-extract-plugin';
import Autoprefixer from 'autoprefixer';
import CssNano from 'cssnano';
import OptimizeCssAssetsPlugin from 'optimize-css-assets-webpack-plugin';
import PostCssSafeParser from 'postcss-safe-parser';

const autoprefix = () => ({
  loader: 'postcss-loader',
  options: {
    plugins: () => [Autoprefixer()],
    sourceMap: true,
  },
});

const lintJS = () => ({

  module: {
    rules: [
      {
        test: /\.js$/,
        enforce: 'pre',
        use: 'eslint-loader',
      },
    ],
  },
});

const transpileJS = ({ entry, include } = {}) => ({
  entry,
  output: {
    chunkFilename: '[name].js',
    filename: '[name].js',
  },
  module: {
    rules: [
      {
        test: /\.js$/,
        include,
        use: 'babel-loader',
      },
    ],
  },
});

const copyNpmDistAssets = ({ modules, dest } = {}) => {
  const pairs = modules.map(m => ({
    from: `node_modules/${m}/dist`,
    to: `${dest}/${m}/[1]`,
    test: new RegExp('.*/dist/(.+/[-.a-z0-9@]+\\.(otf|eot|woff|woff2|ttf|js|js.map|gif|png|jpg|svg|ico))$', 'i'),
  }));

  return {
    plugins: [
      new CopyWebpackPlugin(pairs),
    ],
  };
};

const copyLocalImages = ({ dest } = {}) => ({
  plugins: [
    new CopyWebpackPlugin([{
      from: 'app/assets/images',
      to: dest,
    }]),
  ],
});

const extractCSS = ({ resolverPaths } = {}) => ({
  entry: {
    style: './app/assets/css/main.less',
  },
  module: {
    rules: [
      {
        test: /\.css$/,
        use: [
          MiniCssExtractPlugin.loader,
          {
            loader: 'css-loader',
            options: {
              url: false,
              sourceMap: true,
            },
          },
          autoprefix(),
        ],
      },
      {
        test: /\.less$/,
        use: [
          MiniCssExtractPlugin.loader,
          {
            loader: 'css-loader',
            options: {
              url: false,
              sourceMap: true,
            },
          },
          autoprefix(),
          {
            loader: 'less-loader',
            options: {
              paths: resolverPaths,
              relativeUrls: false,
              sourceMap: true,
            },
          },
        ],
      },
    ],
  },
  plugins: [
    new MiniCssExtractPlugin({
      filename: '[name].css',
    }),
  ],
});


const minify = () => ({
  optimization: {
    minimizer: [
      new TerserPlugin({
        sourceMap: true,
        cache: true,
        terserOptions: {
          compress: {
            drop_console: true,
          },
        },
      }),
      new OptimizeCssAssetsPlugin({
        cssProcessor: CssNano,
        cssProcessorOptions: {
          parser: PostCssSafeParser,
          discardComments: {
            removeAll: true,
          },
        },
        canPrint: true,
      }),
    ],
  },
});


const generateSourceMaps = (devtool) => ({
  devtool,
});

export {
  copyNpmDistAssets,
  copyLocalImages,
  lintJS,
  transpileJS,
  extractCSS,
  minify,
  generateSourceMaps,
};

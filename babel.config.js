module.exports = function (api) {
  api.cache(true);
  return {
    presets: ["babel-preset-expo"],
    overrides: [
      // Hermes (the Android JS engine) can't compile native "#field" private
      // class syntax yet. A dependency somewhere in node_modules ships code
      // using it, pre-compiled for browsers rather than Hermes, and Metro's
      // default transform skips re-lowering syntax inside node_modules for
      // speed. Force these specific down-leveling plugins onto everything
      // under node_modules so that syntax gets converted to something
      // Hermes can run, regardless of which package it came from.
      {
        test: /node_modules/,
        plugins: [
          "@babel/plugin-transform-class-properties",
          "@babel/plugin-transform-private-methods",
          "@babel/plugin-transform-private-property-in-object",
        ],
      },
    ],
  };
};

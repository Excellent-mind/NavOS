module.exports = function (api) {
  api.cache(true);
  return {
    presets: ["babel-preset-expo"],
    overrides: [
      {
        // Why this exists (verified by reading babel-preset-expo's actual
        // source, not guessed): for the Hermes engine, the preset DOES ship
        // plugins that down-level native "#privateField" class syntax
        // (which Hermes can't run), but it only runs them on classes that
        // also use a decorator - an internal "lazy decorators" optimization
        // in babel-preset-expo/src/plugins/lazy-decorators-plugin.ts. React
        // Native's own EventEmitter class (node_modules/react-native) uses
        // plain "#registry" private fields with no decorator, so it falls
        // through that gap and reaches Hermes untransformed.
        //
        // Fix: force the same three plugins to run unconditionally on
        // node_modules, using the exact same options (loose: true) the
        // preset itself uses internally - see hermes-v1.ts's own
        // `const loose = true` comment. A prior attempt without `loose`
        // broke plain `class Foo {}` declarations elsewhere; matching the
        // preset's own loose-mode convention avoids that.
        test: /node_modules/,
        plugins: [
          ["@babel/plugin-transform-class-properties", { loose: true }],
          ["@babel/plugin-transform-private-methods", { loose: true }],
          ["@babel/plugin-transform-private-property-in-object", { loose: true }],
        ],
      },
    ],
  };
};

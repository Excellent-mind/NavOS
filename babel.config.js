module.exports = function (api) {
  api.cache(true);
  return {
    // babel-preset-expo only enables Hermes-specific transforms (including
    // the ones that convert "#privateField" syntax into something Hermes
    // can run) when it detects the target engine as "hermes-stable". That
    // detection depends on app.json's top-level "jsEngine": "hermes" - see
    // that file. Do not add manual private-field/class-properties plugin
    // overrides here: stacking them on top of the preset's own (correctly
    // engine-gated) handling corrupts the output instead of fixing it.
    presets: ["babel-preset-expo"],
  };
};

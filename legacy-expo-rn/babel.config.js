module.exports = function (api) {
  api.cache(true);
  return {
    presets: [
      [
        "babel-preset-expo",
        {
          // Our Android build runs on *classic* Hermes (RN 0.83.6 pins
          // hermes-engine v0.14.1 for Android - confirmed by reading
          // react-native's own sdks/hermes-engine/version.properties).
          // Classic Hermes has NO support at all for native "#privateField"
          // class syntax (confirmed by reading Hermes's own source at tag
          // hermes-v0.14.1: lib/AST/SemanticValidator.cpp literally emits
          // "private properties are not supported" for any private field).
          //
          // babel-preset-expo defaults to assuming the *newer* "Hermes V1"
          // engine (which does support private fields natively) whenever
          // engine === 'hermes', and in that mode it only lowers private
          // fields on classes that also use a decorator (an internal "lazy
          // decorators" optimization) - which is why React Native's own
          // EventEmitter.js (#registry, no decorator) was reaching Hermes
          // untransformed.
          //
          // Setting this explicitly switches to babel-preset-expo's
          // "classic Hermes" preset (configs/hermes-v0.ts, literally
          // commented "The JS syntax preset used with Hermes v0/legacy (pre
          // SDK 56)"), which unconditionally lowers class-properties,
          // private-methods and private-property-in-object for every file,
          // in the correct order (after Flow/TypeScript stripping, which a
          // manually bolted-on override can't replicate - that's what
          // caused the earlier "invalid statement encountered" corruption).
          unstable_transformProfile: "hermes-v0",
        },
      ],
    ],
  };
};

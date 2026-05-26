// https://docs.expo.dev/guides/using-eslint/
const { defineConfig } = require('eslint/config');
const expoConfig = require("eslint-config-expo/flat");

module.exports = defineConfig([
  expoConfig,
  {
    ignores: ["dist/*"],
    rules: {
      "react-hooks/immutability": "off",
      "no-restricted-imports": [
        "error",
        {
          "paths": [
            {
              "name": "@expo/vector-icons",
              "message": "Please import and use AppIcon or react-native-paper components/mechanisms instead."
            },
            {
              "name": "react-native-vector-icons",
              "message": "Please import and use AppIcon or react-native-paper components/mechanisms instead."
            }
          ],
          "patterns": [
            {
              "group": ["@expo/vector-icons/*", "react-native-vector-icons/*", "@react-native-vector-icons/*"],
              "message": "Please import and use AppIcon or react-native-paper components/mechanisms instead."
            }
          ]
        }
      ]
    },
  }
]);

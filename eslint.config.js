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
  ,
  {
    files: ["src/app/**/*.ts", "src/app/**/*.tsx", "src/components/**/*.ts", "src/components/**/*.tsx"],
    rules: {
      "no-restricted-imports": [
        "error",
        {
          "paths": [
            {
              "name": "firebase/auth",
              "message": "UI files must not call Firebase directly. Go through store actions or persistence boundaries."
            },
            {
              "name": "firebase/firestore",
              "message": "UI files must not call Firebase directly. Go through store actions or persistence boundaries."
            },
            {
              "name": "../services/firebase",
              "message": "UI files must not call Firebase services directly. Use store actions only."
            },
            {
              "name": "../../services/firebase",
              "message": "UI files must not call Firebase services directly. Use store actions only."
            }
          ]
        }
      ]
    },
  }
]);

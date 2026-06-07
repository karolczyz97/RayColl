const abiFilter = process.env.ABI_FILTER;

module.exports = {
  expo: {
    name: "RayColl",
    slug: "raycoll-io",
    version: require('./package.json').version,
    orientation: "portrait",
    icon: "./assets/images/icon.png",
    scheme: "raycoll",
    userInterfaceStyle: "automatic",
    ios: {
      icon: "./assets/expo.icon",
      bundleIdentifier: "com.raycoll.app",
      infoPlist: {
        ITSAppUsesNonExemptEncryption: false
      }
    },
    android: {
      package: "com.raycoll.app",
      permissions: [
        "RECORD_AUDIO",
        "INTERNET"
      ],
      adaptiveIcon: {
        backgroundColor: "#E6F4FE",
        foregroundImage: "./assets/images/android-icon-foreground.png",
        backgroundImage: "./assets/images/android-icon-background.png",
        monochromeImage: "./assets/images/android-icon-monochrome.png"
      },
      predictiveBackGestureEnabled: true
    },
    web: {
      bundler: "metro",
      output: "static",
      favicon: "./assets/images/favicon.png"
    },
    plugins: [
      "expo-router",
      "expo-asset",
      [
        "expo-splash-screen",
        {
          "backgroundColor": "#208AEF",
          "image": "./assets/images/splash-icon.png",
          "imageWidth": 76
        }
      ],
      "@react-native-voice/voice",
      "expo-sharing",
      [
        "expo-build-properties",
        {
          "android": {
            "enableOnBackInvokedCallback": true,
            ...(abiFilter ? { buildArchs: [abiFilter] } : {})
          }
        }
      ]
    ],
    experiments: {
      typedRoutes: true,
      reactCompiler: true
    },
    extra: {
      router: {},
      eas: {
        projectId: "077bddf4-8cc0-4aff-8297-71a6b2ca2e37"
      }
    },
    owner: "karolczyz97",
    runtimeVersion: {
      policy: "appVersion"
    },
    updates: {
      url: "https://u.expo.dev/077bddf4-8cc0-4aff-8297-71a6b2ca2e37"
    }
  }
};

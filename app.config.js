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
        "INTERNET",
        "FOREGROUND_SERVICE",
        "FOREGROUND_SERVICE_MEDIA_PLAYBACK"
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
      [
        "expo-speech-recognition",
        {
          "microphonePermission": "Allow RayColl to use the microphone for pronunciation practice.",
          "speechRecognitionPermission": "Allow RayColl to recognize your speech for pronunciation practice.",
          "androidSpeechServicePackages": ["com.google.android.googlequicksearchbox"]
        }
      ],
      "expo-sharing",
      [
        "expo-build-properties",
        {
          "ios": {
            "deploymentTarget": "16.4"
          },
          "android": {}
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
      policy: "fingerprint"
    },
    updates: {
      url: "https://u.expo.dev/077bddf4-8cc0-4aff-8297-71a6b2ca2e37"
    }
  }
};

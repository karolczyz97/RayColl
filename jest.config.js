module.exports = {
  preset: 'jest-expo',
  setupFilesAfterEnv: ['<rootDir>/jest.setup.js'],
  testMatch: [
    '**/__tests__/**/*.test.{ts,tsx}',
  ],
  // Mirrors jest-expo's default allowlist, plus @pchmn/expo-material3-theme and
  // its @material/material-color-utilities dependency, which ship ESM and would
  // otherwise fail to parse once the theme imports the Material You generator.
  transformIgnorePatterns: [
    '/node_modules/(?!(.pnpm|react-native|@react-native|@react-native-community|expo|@expo|@expo-google-fonts|react-navigation|@react-navigation|@sentry/react-native|native-base|standard-navigation|@pchmn|@material))',
    '/node_modules/react-native-reanimated/plugin/',
    '/node_modules/@react-native/babel-preset/',
  ],
};

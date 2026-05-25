import React from 'react';
import { Stack } from 'expo-router';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { PaperProvider, MD3LightTheme, MD3DarkTheme } from 'react-native-paper';
import { I18nProvider, useI18n } from '../i18n';
import { AppThemeProvider, useAppTheme } from '../contexts/ThemeContext';

// Custom theme colors for a premium glassmorphic and high contrast look
const lightTheme = {
  ...MD3LightTheme,
  colors: {
    ...MD3LightTheme.colors,
    primary: '#208AEF',
    secondary: '#7c4dff',
    background: '#f4f6fa',
    surface: '#ffffff',
    error: '#d32f2f',
  },
};

const darkTheme = {
  ...MD3DarkTheme,
  colors: {
    ...MD3DarkTheme.colors,
    primary: '#4ea8ff',
    secondary: '#b39ddb',
    background: '#121212',
    surface: '#1e1e1e',
    error: '#cf6679',
  },
};

function InnerLayout() {
  const { isI18nLoading } = useI18n();
  const { isDark } = useAppTheme();
  const theme = isDark ? darkTheme : lightTheme;

  if (isI18nLoading) {
    return null; // Let the splash screen stay visible or show a loading indicator
  }

  return (
    <PaperProvider theme={theme}>
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="index" />
        <Stack.Screen name="study/[groupId]" />
        <Stack.Screen name="browse/[groupId]" />
        <Stack.Screen name="import" />
        <Stack.Screen name="settings/[groupId]" />
        <Stack.Screen name="stats" />
        <Stack.Screen name="app-settings" />
      </Stack>
    </PaperProvider>
  );
}

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <I18nProvider>
        <AppThemeProvider>
          <InnerLayout />
        </AppThemeProvider>
      </I18nProvider>
    </SafeAreaProvider>
  );
}

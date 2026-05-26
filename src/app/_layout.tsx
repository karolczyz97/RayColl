import React from 'react';
import { Stack } from 'expo-router';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { PaperProvider, MD3LightTheme, MD3DarkTheme } from 'react-native-paper';
import { Platform, View, StyleSheet } from 'react-native';
import { useMaterial3Theme } from '@pchmn/expo-material3-theme';
import * as SplashScreen from 'expo-splash-screen';
import { I18nProvider, useI18n } from '../i18n';
import { AppThemeProvider, useAppTheme } from '../contexts/ThemeContext';

// Prevent the splash screen from auto-hiding before asset loading is complete.
SplashScreen.preventAutoHideAsync().catch(() => {});

// MD3 Expressive — refined color palette with richer tones and better contrast
const lightTheme = {
  ...MD3LightTheme,
  colors: {
    ...MD3LightTheme.colors,
    primary: '#1565c0',
    onPrimary: '#ffffff',
    primaryContainer: '#d1e4ff',
    onPrimaryContainer: '#001d36',
    secondary: '#6750a4',
    onSecondary: '#ffffff',
    secondaryContainer: '#e8def8',
    tertiary: '#006c4c',
    tertiaryContainer: '#89f8c7',
    background: '#faf8ff',
    onBackground: '#1b1b1f',
    surface: '#ffffff',
    surfaceVariant: '#e7e0ec',
    onSurface: '#1b1b1f',
    onSurfaceVariant: '#49454f',
    outline: '#79747e',
    outlineVariant: '#cac4d0',
    error: '#ba1a1a',
    onError: '#ffffff',
    errorContainer: '#ffdad6',
  },
};

const darkTheme = {
  ...MD3DarkTheme,
  colors: {
    ...MD3DarkTheme.colors,
    primary: '#a0cafd',
    onPrimary: '#003258',
    primaryContainer: '#00497d',
    onPrimaryContainer: '#d1e4ff',
    secondary: '#cfbcff',
    onSecondary: '#381e72',
    secondaryContainer: '#4f378b',
    tertiary: '#6ddbac',
    tertiaryContainer: '#005138',
    background: '#0f0f14',
    onBackground: '#e6e1e5',
    surface: '#1c1b1f',
    surfaceVariant: '#2b2930',
    onSurface: '#e6e1e5',
    onSurfaceVariant: '#cac4d0',
    outline: '#938f99',
    outlineVariant: '#49454f',
    error: '#ffb4ab',
    onError: '#690005',
    errorContainer: '#93000a',
  },
};

function InnerLayout() {
  const { isI18nLoading } = useI18n();
  const { isDark, useSystemColors, isThemeLoading } = useAppTheme();
  const { theme: materialColors } = useMaterial3Theme();

  const theme = React.useMemo(() => {
    if (useSystemColors) {
      const baseTheme = isDark ? MD3DarkTheme : MD3LightTheme;
      const colors = isDark ? materialColors.dark : materialColors.light;
      return {
        ...baseTheme,
        colors: {
          ...baseTheme.colors,
          ...colors,
        },
      };
    } else {
      return isDark ? darkTheme : lightTheme;
    }
  }, [isDark, useSystemColors, materialColors]);

  // Hide the splash screen only after settings and translations are loaded.
  // Icon fonts are now auto-loaded by @react-native-vector-icons.
  React.useEffect(() => {
    const isReady = !isI18nLoading && !isThemeLoading;
    if (isReady) {
      SplashScreen.hideAsync().catch(() => {});
    }
  }, [isI18nLoading, isThemeLoading]);

  if (isI18nLoading || isThemeLoading) {
    return null; // Let the splash screen stay visible
  }

  const content = (
    <View style={[styles.rootContainer, { backgroundColor: theme.colors.background }]}>
      <Stack screenOptions={{ headerShown: false }}>
        <Stack.Screen name="index" />
        <Stack.Screen name="study/[groupId]" />
        <Stack.Screen name="browse/[groupId]" />
        <Stack.Screen name="import" />
        <Stack.Screen name="settings/[groupId]" />
        <Stack.Screen name="stats" />
        <Stack.Screen name="app-settings" />
      </Stack>
    </View>
  );

  return (
    <PaperProvider theme={theme}>
      {Platform.OS === 'web' ? (
        <View style={[styles.webOuter, { backgroundColor: theme.colors.surfaceVariant }]}>
          <View style={[styles.webInner, { backgroundColor: theme.colors.background }]}>
            {content}
          </View>
        </View>
      ) : (
        content
      )}
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

const styles = StyleSheet.create({
  rootContainer: {
    flex: 1,
    width: '100%',
  },
  webOuter: {
    flex: 1,
    width: '100%',
    height: '100%',
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    alignItems: 'center',
    justifyContent: 'center',
  },
  webInner: {
    flex: 1,
    width: '100%',
    maxWidth: 960,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.08,
    shadowRadius: 16,
    elevation: 4,
  },
});

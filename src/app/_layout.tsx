import React from 'react';
import { Stack } from 'expo-router';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { PaperProvider, MD3LightTheme, MD3DarkTheme } from 'react-native-paper';
import { Platform, View, StyleSheet } from 'react-native';
import { useFonts } from 'expo-font';
import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { useMaterial3Theme } from '@pchmn/expo-material3-theme';
import * as SplashScreen from 'expo-splash-screen';
import { I18nProvider, useI18n } from '../i18n';
import { AppThemeProvider, useAppTheme } from '../contexts/ThemeContext';

// Prevent the splash screen from auto-hiding before asset loading is complete.
SplashScreen.preventAutoHideAsync().catch(() => {});

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

function InnerLayout({ fontsLoaded, fontError }: { fontsLoaded: boolean; fontError: any }) {
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

  // Hide the splash screen only after all fonts, settings, and translations are loaded.
  React.useEffect(() => {
    const isReady = (fontsLoaded || fontError) && !isI18nLoading && !isThemeLoading;
    if (isReady) {
      SplashScreen.hideAsync().catch(() => {});
    }
  }, [fontsLoaded, fontError, isI18nLoading, isThemeLoading]);

  if (!(fontsLoaded || fontError) || isI18nLoading || isThemeLoading) {
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
        <View style={[styles.webOuter, { backgroundColor: isDark ? '#0f0f0f' : '#eaecef' }]}>
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
  const [fontsLoaded, fontError] = useFonts({
    ...MaterialCommunityIcons.font,
  });

  return (
    <SafeAreaProvider>
      <I18nProvider>
        <AppThemeProvider>
          <InnerLayout fontsLoaded={fontsLoaded} fontError={fontError} />
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
    shadowOpacity: 0.1,
    shadowRadius: 12,
    elevation: 4,
  },
});

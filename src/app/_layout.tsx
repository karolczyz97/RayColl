import React from 'react';
import { Stack } from 'expo-router';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { PaperProvider } from 'react-native-paper';
import { Platform, View, StyleSheet } from 'react-native';
import { useMaterial3Theme } from '@pchmn/expo-material3-theme';
import * as SplashScreen from 'expo-splash-screen';
import { I18nProvider, useI18n } from '../i18n';
import { AppThemeProvider, useAppTheme } from '../contexts/ThemeContext';
import { FlashcardStoreProvider } from '../hooks/useFlashcardStore';
import { createAppTheme } from '../theme/createAppTheme';

// Prevent the splash screen from auto-hiding before asset loading is complete.
SplashScreen.preventAutoHideAsync().catch(() => {});

function InnerLayout() {
  const { isI18nLoading } = useI18n();
  const { isDark, useSystemColors, isThemeLoading } = useAppTheme();
  const { theme: materialColors } = useMaterial3Theme();

  const theme = React.useMemo(() => {
    return createAppTheme({
      isDark,
      useSystemColors,
      materialColors,
    });
  }, [isDark, useSystemColors, materialColors]);

  // Hide the splash screen only after settings, translations, and fonts are loaded.
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
          <FlashcardStoreProvider>
            <InnerLayout />
          </FlashcardStoreProvider>
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

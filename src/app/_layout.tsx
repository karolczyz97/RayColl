import React from 'react';
import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { ExperimentalStack as Stack } from 'expo-router';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { PaperProvider } from 'react-native-paper';
import { Platform, View, StyleSheet } from 'react-native';
import { useMaterial3Theme } from '@pchmn/expo-material3-theme';
import * as SplashScreen from 'expo-splash-screen';
import { I18nProvider, useI18n } from '../i18n';
import { AppThemeProvider, useAppTheme } from '../contexts/ThemeContext';
import { FlashcardStoreProvider } from '../hooks/useFlashcardStore';
import { createAppTheme } from '../theme/createAppTheme';
import { UpdateNotification } from '../components/feedback/UpdateNotification';

function logSplashScreenError(action: string, error: unknown) {
  const message = error instanceof Error ? error.message : String(error);
  console.warn(`Splash screen ${action} failed:`, message);
}

interface PaperIconRendererProps {
  allowFontScaling?: boolean;
  color?: string;
  direction: 'ltr' | 'rtl';
  name: React.ComponentProps<typeof MaterialCommunityIcons>['name'];
  size: number;
  testID?: string;
}

function renderPaperIcon({
  allowFontScaling,
  color,
  direction,
  name,
  size,
  testID,
}: PaperIconRendererProps) {
  return (
    <MaterialCommunityIcons
      allowFontScaling={allowFontScaling}
      color={color}
      name={name}
      size={size}
      style={{
        lineHeight: size,
        transform: [{ scaleX: direction === 'rtl' ? -1 : 1 }],
      }}
      testID={testID}
    />
  );
}

// Prevent the splash screen from auto-hiding before asset loading is complete.
SplashScreen.preventAutoHideAsync().catch((err: unknown) => {
  logSplashScreenError('prevent auto hide', err);
});

function InnerLayout() {
  const { isI18nLoading } = useI18n();
  const { isDark, useSystemColors, isThemeLoading } = useAppTheme();
  const { theme: materialColors } = useMaterial3Theme();
  const [isIconFontReady, setIsIconFontReady] = React.useState(Platform.OS !== 'web');

  const theme = React.useMemo(() => {
    return createAppTheme({
      isDark,
      useSystemColors,
      materialColors,
    });
  }, [isDark, useSystemColors, materialColors]);

  React.useEffect(() => {
    if (Platform.OS !== 'web') {
      return;
    }

    let cancelled = false;

    MaterialCommunityIcons.loadFont()
      .then(() => {
        if (!cancelled) {
          setIsIconFontReady(true);
        }
      })
      .catch((err: unknown) => {
        const message = err instanceof Error ? err.message : String(err);
        console.warn('MaterialCommunityIcons font preload failed:', message);
        if (!cancelled) {
          setIsIconFontReady(true);
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  // Hide the splash screen only after settings, translations, and fonts are loaded.
  React.useEffect(() => {
    const isReady = !isI18nLoading && !isThemeLoading && isIconFontReady;
    if (isReady) {
      SplashScreen.hideAsync().catch((err: unknown) => {
        logSplashScreenError('hide', err);
      });
    }
  }, [isI18nLoading, isThemeLoading, isIconFontReady]);

  if (isI18nLoading || isThemeLoading || !isIconFontReady) {
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
    <PaperProvider theme={theme} settings={{ icon: renderPaperIcon }}>
      {Platform.OS === 'web' ? (
        <View style={[styles.webOuter, { backgroundColor: theme.colors.surfaceVariant }]}>
          <View
            style={[
              styles.webInner,
              { backgroundColor: theme.colors.background, shadowColor: theme.colors.shadow },
            ]}
          >
            {content}
          </View>
        </View>
      ) : (
        content
      )}
      <UpdateNotification />
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
    minHeight: 0,
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
    minHeight: 0,
  },
  webInner: {
    flex: 1,
    width: '100%',
    maxWidth: 1200,
    minHeight: 0,
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.08,
    shadowRadius: 16,
    elevation: 4,
  },
});

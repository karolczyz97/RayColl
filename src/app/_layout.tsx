import React from 'react';
// eslint-disable-next-line no-restricted-imports -- required as PaperProvider icon renderer
import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { ExperimentalStack as Stack } from 'expo-router';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { PaperProvider, useTheme } from 'react-native-paper';
import { Platform, View, StyleSheet } from 'react-native';
import { useMaterial3Theme } from '@pchmn/expo-material3-theme';
import * as SplashScreen from 'expo-splash-screen';
import { I18nProvider, useI18n } from '@/i18n';
import { AppThemeProvider, useAppTheme } from '@/contexts/ThemeContext';
import { FlashcardStoreProvider } from '@/hooks/useFlashcardStore';
import { createAppTheme } from '@/theme/createAppTheme';
import { UpdateNotification } from '@/components/feedback/UpdateNotification';
import { AppNavigationShell } from '@/components/navigation/AppNavigationShell';
import { getErrorMessage } from '@/utils/errors';

function logSplashScreenError(action: string, error: unknown) {
  console.warn(`Splash screen ${action} failed:`, getErrorMessage(error));
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

function themeColorWithAlpha(color: string, alpha: number): string {
  const normalized = color.startsWith('#') ? color.slice(1) : color;
  const expanded =
    normalized.length === 3
      ? normalized
          .split('')
          .map((part) => `${part}${part}`)
          .join('')
      : normalized;

  if (!/^[0-9a-fA-F]{6}$/.test(expanded)) {
    return color;
  }

  const red = Number.parseInt(expanded.slice(0, 2), 16);
  const green = Number.parseInt(expanded.slice(2, 4), 16);
  const blue = Number.parseInt(expanded.slice(4, 6), 16);

  return `rgba(${red}, ${green}, ${blue}, ${alpha})`;
}

// Prevent the splash screen from auto-hiding before asset loading is complete.
SplashScreen.preventAutoHideAsync().catch((err: unknown) => {
  logSplashScreenError('prevent auto hide', err);
});

function ThemedPaperProvider({ children }: { children: React.ReactNode }) {
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

  // Inject global scrollbar styles on web.
  React.useEffect(() => {
    if (Platform.OS !== 'web' || typeof document === 'undefined') {
      return;
    }
    const styleId = 'raycoll-global-scrollbar';
    if (document.getElementById(styleId)) {
      return;
    }
    const style = document.createElement('style');
    style.id = styleId;
    style.textContent = `
      html {
        scrollbar-width: thin;
        scrollbar-color: var(--raycoll-scrollbar-thumb) transparent;
      }
      .raycoll-stable-scrollbar {
        scrollbar-gutter: stable both-edges;
      }
      *::-webkit-scrollbar {
        width: 8px;
        height: 8px;
        background: transparent;
      }
      *::-webkit-scrollbar-track {
        background: transparent;
      }
      *::-webkit-scrollbar-thumb {
        background-color: var(--raycoll-scrollbar-thumb);
        border-radius: 4px;
      }
      *::-webkit-scrollbar-thumb:hover {
        background-color: var(--raycoll-scrollbar-thumb-hover);
      }
      *::-webkit-scrollbar-corner {
        background: transparent;
      }
    `;
    document.head.appendChild(style);
  }, []);

  React.useEffect(() => {
    if (Platform.OS !== 'web' || typeof document === 'undefined') {
      return;
    }

    document.documentElement.style.setProperty(
      '--raycoll-scrollbar-thumb',
      themeColorWithAlpha(theme.colors.outline, 0.4),
    );
    document.documentElement.style.setProperty(
      '--raycoll-scrollbar-thumb-hover',
      themeColorWithAlpha(theme.colors.outline, 0.64),
    );
  }, [theme.colors.outline]);

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
        console.warn('MaterialCommunityIcons font preload failed:', getErrorMessage(err));
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

  return (
    <PaperProvider theme={theme} settings={{ icon: renderPaperIcon }}>
      {children}
    </PaperProvider>
  );
}

function AppContent() {
  const theme = useTheme();

  const content = (
    <View style={[styles.rootContainer, { backgroundColor: theme.colors.background }]}>
      <AppNavigationShell>
        <Stack screenOptions={{ headerShown: false }}>
          <Stack.Screen name="index" />
          <Stack.Screen name="study/[groupId]" />
          <Stack.Screen name="browse/[groupId]" />
          <Stack.Screen name="import" />
          <Stack.Screen name="settings/[groupId]" />
          <Stack.Screen name="stats" />
          <Stack.Screen name="app-settings" />
        </Stack>
      </AppNavigationShell>
    </View>
  );

  return (
    <>
      {Platform.OS === 'web' ? (
        <View style={[styles.webOuter, { backgroundColor: theme.colors.background }]}>
          {content}
        </View>
      ) : (
        content
      )}
      <UpdateNotification />
    </>
  );
}

export default function RootLayout() {
  return (
    <SafeAreaProvider>
      <I18nProvider>
        <AppThemeProvider>
          <ThemedPaperProvider>
            <FlashcardStoreProvider>
              <AppContent />
            </FlashcardStoreProvider>
          </ThemedPaperProvider>
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
    minHeight: 0,
  },
});

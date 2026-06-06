import React from 'react';
// eslint-disable-next-line no-restricted-imports -- required as PaperProvider icon renderer
import MaterialCommunityIcons from '@expo/vector-icons/MaterialCommunityIcons';
import { ExperimentalStack as Stack, usePathname } from 'expo-router';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { PaperProvider, useTheme } from 'react-native-paper';
import { Platform, View, StyleSheet } from 'react-native';
import { useMaterial3Theme } from '@pchmn/expo-material3-theme';
import { useFonts } from 'expo-font';
import * as SplashScreen from 'expo-splash-screen';
import { I18nProvider, useI18n } from '@/i18n';
import { AppThemeProvider, useAppTheme } from '@/contexts/UserPreferencesContext';
import { FlashcardStoreProvider } from '@/store/FlashcardStoreContext';
import { createAppTheme } from '@/theme/createAppTheme';
import { AppErrorBoundary } from '@/components/feedback/AppErrorBoundary';
import { UpdateNotification } from '@/components/feedback/UpdateNotification';
import { AppNavigationShell } from '@/components/navigation/AppNavigationShell';
import { hexToRgba } from '@/theme/colorUtils';
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

// Prevent the splash screen from auto-hiding before asset loading is complete.
SplashScreen.preventAutoHideAsync().catch((err: unknown) => {
  logSplashScreenError('prevent auto hide', err);
});

function getGlobalErrorTitleKey(pathname: string): string | undefined {
  switch (pathname) {
    case '/':
      return 'errors.dashboard_crashed';
    case '/stats':
      return 'errors.stats_crashed';
    case '/app-settings':
      return 'errors.app_settings_crashed';
    default:
      return undefined;
  }
}

// Hard ceiling on how long the web app waits for the icon font before rendering
// anyway. expo-font preloads it (see useFonts), so this only matters if the font
// fetch hangs without ever resolving or rejecting — render rather than hold an
// indefinite splash.
const ICON_FONT_MAX_WAIT_MS = 4000;

function ThemedPaperProvider({ children }: { children: React.ReactNode }) {
  const { isI18nLoading } = useI18n();
  const { isDark, useSystemColors, isThemeLoading } = useAppTheme();
  const { theme: materialColors } = useMaterial3Theme();
  // Registering the icon font through expo-font (rather than the imperative
  // loadFont() in an effect) lets the static web export embed a font preload in
  // the HTML, so the font downloads in parallel with the JS bundle instead of
  // being requested only after the first client render.
  const [iconFontLoaded, iconFontError] = useFonts(MaterialCommunityIcons.font);
  const [iconFontRecovered, setIconFontRecovered] = React.useState(false);
  const isIconFontReady = Platform.OS !== 'web' || iconFontLoaded || iconFontRecovered;

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
      hexToRgba(theme.colors.outline, 0.4),
    );
    document.documentElement.style.setProperty(
      '--raycoll-scrollbar-thumb-hover',
      hexToRgba(theme.colors.outline, 0.64),
    );
  }, [theme.colors.outline]);

  // expo-font preloads the icon font (see useFonts above). If that load fails —
  // e.g. a cold-CDN hiccup right after a web deploy — retry a few times before
  // giving up, so a transient failure no longer leaves the app with blank icons
  // until the user manually reloads.
  React.useEffect(() => {
    if (Platform.OS !== 'web' || !iconFontError || iconFontRecovered) {
      return;
    }

    let cancelled = false;

    void (async () => {
      for (let attempt = 1; attempt <= 3 && !cancelled; attempt += 1) {
        try {
          await MaterialCommunityIcons.loadFont();
          if (!cancelled) {
            setIconFontRecovered(true);
          }
          return;
        } catch (err: unknown) {
          if (attempt === 3) {
            console.warn('MaterialCommunityIcons font load failed after retries:', getErrorMessage(err));
          } else {
            await new Promise((resolve) => setTimeout(resolve, attempt * 300));
          }
        }
      }
      // Last resort: render without the icon font rather than blocking forever.
      if (!cancelled) {
        setIconFontRecovered(true);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [iconFontError, iconFontRecovered]);

  // Hard cap: render even if the icon font never resolves nor rejects (a hang,
  // not an error). Prevents an indefinite splash if the font fetch stalls.
  React.useEffect(() => {
    if (Platform.OS !== 'web' || iconFontLoaded || iconFontRecovered) {
      return;
    }
    const timer = setTimeout(() => setIconFontRecovered(true), ICON_FONT_MAX_WAIT_MS);
    return () => clearTimeout(timer);
  }, [iconFontLoaded, iconFontRecovered]);

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
  const pathname = usePathname();
  const { t } = useI18n();
  const globalErrorTitleKey = getGlobalErrorTitleKey(pathname);

  const content = (
    <AppErrorBoundary
      resetKey={pathname}
      title={globalErrorTitleKey ? t(globalErrorTitleKey) : undefined}
    >
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
      <UpdateNotification />
    </AppErrorBoundary>
  );

  if (Platform.OS === 'web') {
    return (
      <View style={[styles.webOuter, { backgroundColor: theme.colors.background }]}>
        {content}
      </View>
    );
  }

  return content;
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

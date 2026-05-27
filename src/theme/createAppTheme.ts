import { MD3LightTheme, MD3DarkTheme, MD3Theme } from 'react-native-paper';
import { Material3Theme } from '@pchmn/expo-material3-theme';
import { TOKENS } from './tokens';

export const lightTheme: MD3Theme = {
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

export const darkTheme: MD3Theme = {
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

export function createAppTheme({
  isDark,
  useSystemColors,
  materialColors,
}: {
  isDark: boolean;
  useSystemColors: boolean;
  materialColors?: Material3Theme;
}): MD3Theme {
  if (useSystemColors && materialColors) {
    const baseTheme = isDark ? MD3DarkTheme : MD3LightTheme;
    const colors = isDark ? materialColors.dark : materialColors.light;
    return {
      ...baseTheme,
      roundness: TOKENS.radius.md,
      colors: {
        ...baseTheme.colors,
        ...colors,
      },
    };
  }
  return {
    ...(isDark ? darkTheme : lightTheme),
    roundness: TOKENS.radius.md,
  };
}

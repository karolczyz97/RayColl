import { MD3LightTheme, MD3DarkTheme, MD3Theme } from 'react-native-paper';
import { Material3Theme } from '@pchmn/expo-material3-theme';
import { TOKENS } from './tokens';

export const lightTheme: MD3Theme = {
  ...MD3LightTheme,
  roundness: TOKENS.radius.md,
};

export const darkTheme: MD3Theme = {
  ...MD3DarkTheme,
  roundness: TOKENS.radius.md,
};

import { Platform } from 'react-native';

export function createAppTheme({
  isDark,
  useSystemColors,
  materialColors,
}: {
  isDark: boolean;
  useSystemColors: boolean;
  materialColors?: Material3Theme;
}): MD3Theme {
  const supportsDynamicColors = Platform.OS === 'android';
  if (supportsDynamicColors && useSystemColors && materialColors) {
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

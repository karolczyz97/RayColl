import { Platform } from 'react-native';
import { MD3LightTheme, MD3DarkTheme, MD3Theme } from 'react-native-paper';
import { createMaterial3Theme } from '@pchmn/expo-material3-theme';
import type { Material3Scheme, Material3Theme } from '@pchmn/expo-material3-theme';
import { TOKENS } from './tokens';

type MD3Colors = MD3Theme['colors'];

// RayColl brand seed. The entire palette — accents, containers, on-colors,
// surface tonal steps, and elevation overlays — is generated from this one color
// via the Material You algorithm (Google's material-color-utilities), for both
// light and dark, instead of hand-picking dozens of hexes. It's the same
// generator the Android system-colors path uses, so the curated theme and the
// dynamic theme are built the same way.
const SEED_COLOR = '#5A4FCF';
const generated = createMaterial3Theme(SEED_COLOR);

// Map a generated Material 3 scheme onto Paper's color object. The page canvas
// keeps the scheme's light `surface`/`background` (near-white in light, near-black
// in dark) while cards and settings tiles paint themselves with the contained
// (MD3 filled-card) surface from `getContainedSurface` — so cards carry their own
// tonal color without a shadow, instead of blending into the background the way
// the default MD3 baseline (surface ≈ background) did. Spreading the whole scheme
// also keeps the full surfaceContainer* ramp on the runtime color object, which is
// where the filled surface is read from.
function paperColorsFromScheme(base: MD3Colors, scheme: Material3Scheme): MD3Colors {
  return {
    ...base,
    ...scheme,
  };
}

const lightColors = paperColorsFromScheme(MD3LightTheme.colors, generated.light);
const darkColors = paperColorsFromScheme(MD3DarkTheme.colors, generated.dark);

export const lightTheme: MD3Theme = {
  ...MD3LightTheme,
  roundness: TOKENS.radius.xl,
  colors: lightColors,
};

export const darkTheme: MD3Theme = {
  ...MD3DarkTheme,
  roundness: TOKENS.radius.xl,
  colors: darkColors,
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
  const supportsDynamicColors = Platform.OS === 'android';
  if (supportsDynamicColors && useSystemColors && materialColors) {
    const baseTheme = isDark ? MD3DarkTheme : MD3LightTheme;
    const scheme = isDark ? materialColors.dark : materialColors.light;
    return {
      ...baseTheme,
      roundness: TOKENS.radius.xl,
      colors: paperColorsFromScheme(baseTheme.colors, scheme),
    };
  }
  return {
    ...(isDark ? darkTheme : lightTheme),
    roundness: TOKENS.radius.xl,
  };
}

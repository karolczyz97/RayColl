import { MD3Theme } from 'react-native-paper';
import { SrsCardCategory } from '@/srs/srsEngine';
import { hexToRgba } from './colorUtils';
import { TOKENS } from './tokens';

// Per-category SRS palette. `bg` is the light tint used as the chip/segment
// fill (onSurface text is drawn on top); `fg` is the saturated/foreground
// variant of the same hue. Hues: new=blue, learning=yellow, review=orange,
// mastered=green.
const SRS_STATUS_COLORS = {
  light: {
    new: { fg: '#1b4fa8', bg: '#c2d9ff' },
    learning: { fg: '#8a6d00', bg: '#f4dd87' },
    review: { fg: '#b4480f', bg: '#ffcfa8' },
    mastered: { fg: '#0c6e4c', bg: '#c2f0d9' },
  },
  dark: {
    new: { fg: '#9ec5ff', bg: '#173a75' },
    learning: { fg: '#f3d44e', bg: '#403300' },
    review: { fg: '#fdba74', bg: '#7c2d12' },
    mastered: { fg: '#7ee2b8', bg: '#063f2c' },
  },
} as const;

const SUCCESS_COLORS = {
  light: { color: '#0f7b55', bg: '#d7f7e7' },
  dark: { color: '#7ee2b8', bg: '#063f2c' },
} as const;

export function getSuccessColor(theme: MD3Theme): string {
  return SUCCESS_COLORS[theme.dark ? 'dark' : 'light'].color;
}

export function getSuccessBgColor(theme: MD3Theme): string {
  return SUCCESS_COLORS[theme.dark ? 'dark' : 'light'].bg;
}

export function getWarningColor(theme: MD3Theme): string {
  return theme.dark ? theme.colors.primary : TOKENS.colors.warning;
}

export function getWarningBgColor(theme: MD3Theme): string {
  return theme.dark ? theme.colors.primaryContainer : TOKENS.colors.warningBg;
}

export function getDueColor(theme: MD3Theme): string {
  return theme.colors.error;
}

export function getDueBgColor(theme: MD3Theme): string {
  return theme.colors.errorContainer;
}

export function getInfoColor(theme: MD3Theme): string {
  return theme.colors.primary;
}

export function getInfoBgColor(theme: MD3Theme): string {
  return theme.colors.primaryContainer;
}

export function getSecondaryColor(theme: MD3Theme): string {
  return theme.colors.secondary;
}

export function getSecondaryBgColor(theme: MD3Theme): string {
  return theme.colors.secondaryContainer;
}

// Top app bar: strong, leading color. Single source of truth so the whole
// shell can be re-toned (e.g. to primaryContainer/onPrimaryContainer) in one place.
export function getTopBarColors(theme: MD3Theme): { bg: string; fg: string } {
  return { bg: theme.colors.primaryContainer, fg: theme.colors.onPrimaryContainer };
}

export function getHeatmapColor(theme: MD3Theme, count: number): string {
  if (count === 0) {
    return theme.colors.surfaceVariant;
  }
  const primary = theme.colors.primary;
  if (count <= 2) return hexToRgba(primary, 0.25);
  if (count <= 5) return hexToRgba(primary, 0.5);
  if (count <= 10) return hexToRgba(primary, 0.75);
  return primary;
}

export function getReviewStatusColor(
  theme: MD3Theme,
  category: SrsCardCategory,
): { fg: string; bg: string } {
  return SRS_STATUS_COLORS[theme.dark ? 'dark' : 'light'][category];
}

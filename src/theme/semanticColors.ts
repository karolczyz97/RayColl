import { MD3Theme } from 'react-native-paper';
import { SrsCardCategory } from '@/srs/srsEngine';
import { hexToRgba } from './colorUtils';
import { TOKENS } from './tokens';

// Per-category SRS palette. The light, pastel tints are shared across light and
// dark mode so the progress colors stay bright in both themes (dark mode used to
// use muted deep fills). `bg` is the pastel fill used for chips, progress
// segments, and filters; `fg` is the darker same-hue content drawn ON that fill.
// Hues: new=blue, learning=yellow, review=orange, mastered=green.
const SRS_STATUS_COLORS: Record<SrsCardCategory, { fg: string; bg: string }> = {
  new: { fg: '#1b4fa8', bg: '#c2d9ff' },
  learning: { fg: '#8a6d00', bg: '#f4dd87' },
  review: { fg: '#b4480f', bg: '#ffcfa8' },
  mastered: { fg: '#0c6e4c', bg: '#c2f0d9' },
};

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

// Top app bar: flush with the screen background so the bar blends into the
// system status-bar area above it (the MD3 default top-app-bar treatment).
// Single source of truth so the whole shell can be re-toned in one place.
export function getTopBarColors(theme: MD3Theme): { bg: string; fg: string } {
  return { bg: theme.colors.background, fg: theme.colors.onBackground };
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

// Theme is accepted for API symmetry with the other color helpers, but the SRS
// palette is intentionally identical in light and dark mode.
export function getReviewStatusColor(
  theme: MD3Theme,
  category: SrsCardCategory,
): { fg: string; bg: string } {
  void theme;
  return SRS_STATUS_COLORS[category];
}

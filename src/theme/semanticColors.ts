import { MD3Theme } from 'react-native-paper';
import { SrsCardCategory } from '@/srs/srsEngine';
import { hexToRgba } from './colorUtils';
import { TOKENS } from './tokens';

const SRS_STATUS_COLORS = {
  light: {
    new: { color: '#5f6368', bg: '#eceff1' },
    learning: { color: TOKENS.colors.warning, bg: TOKENS.colors.warningBg },
    review: { color: '#2d6cdf', bg: '#dbeafe' },
    mastered: { color: '#0f7b55', bg: '#d7f7e7' },
  },
  dark: {
    new: { color: '#c4c7c5', bg: '#323539' },
    learning: { color: '#ffd180', bg: '#4a3000' },
    review: { color: '#9ec5ff', bg: '#173a75' },
    mastered: { color: '#7ee2b8', bg: '#063f2c' },
  },
} as const;

export function getSuccessColor(theme: MD3Theme): string {
  // Tertiary is green in our theme, representing success
  return theme.colors.tertiary;
}

export function getSuccessBgColor(theme: MD3Theme): string {
  return theme.colors.tertiaryContainer;
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
): { color: string; bg: string } {
  return SRS_STATUS_COLORS[theme.dark ? 'dark' : 'light'][category];
}

import { MD3Theme } from 'react-native-paper';
import { SrsCardCategory } from '@/srs/srsEngine';
import { hexToRgba } from './colorUtils';
import { TOKENS } from './tokens';

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
  switch (category) {
    case 'new':
      return {
        color: getInfoColor(theme),
        bg: getInfoBgColor(theme),
      };
    case 'learning':
      // W toku -> purple (secondary)
      return {
        color: getSecondaryColor(theme),
        bg: getSecondaryBgColor(theme),
      };
    case 'review':
      // Review -> orange (warning)
      return {
        color: getWarningColor(theme),
        bg: getWarningBgColor(theme),
      };
    case 'mastered':
      return {
        color: getSuccessColor(theme),
        bg: getSuccessBgColor(theme),
      };
  }
}

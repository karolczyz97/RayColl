import { MD3Theme } from 'react-native-paper';
import { SrsCardCategory } from '../srs/srsEngine';

export function getSuccessColor(theme: MD3Theme): string {
  // Tertiary is green in our theme, representing success
  return theme.colors.tertiary;
}

export function getSuccessBgColor(theme: MD3Theme): string {
  return theme.colors.tertiaryContainer;
}

export function getWarningColor(theme: MD3Theme): string {
  return theme.dark ? theme.colors.primary : '#b86800';
}

export function getWarningBgColor(theme: MD3Theme): string {
  return theme.dark ? theme.colors.primaryContainer : '#ffddb3';
}

export function getDueColor(theme: MD3Theme): string {
  return theme.colors.error;
}

export function getDueBgColor(theme: MD3Theme): string {
  return theme.colors.errorContainer;
}

export function getDangerColor(theme: MD3Theme): string {
  return theme.colors.error;
}

export function getDangerBgColor(theme: MD3Theme): string {
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
  // If color is hex, we can apply opacities
  if (primary.startsWith('#')) {
    const base =
      primary.length === 4
        ? `#${primary[1]}${primary[1]}${primary[2]}${primary[2]}${primary[3]}${primary[3]}`
        : primary;
    if (count <= 2) return `${base}40`; // 25% opacity
    if (count <= 5) return `${base}80`; // 50% opacity
    if (count <= 10) return `${base}c0`; // 75% opacity
    return base; // 100% opacity
  }
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
      // Powtórki -> orange (warning)
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

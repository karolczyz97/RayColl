import type { MD3Theme } from 'react-native-paper';

export type ExpressiveColorRole = 'primary' | 'secondary' | 'tertiary' | 'error' | 'surface';

export function getExpressiveColors(theme: MD3Theme, role: ExpressiveColorRole) {
  switch (role) {
    case 'secondary':
      return {
        fill: theme.colors.secondary,
        container: theme.colors.secondaryContainer,
        outline: theme.colors.outlineVariant,
      };
    case 'tertiary':
      return {
        fill: theme.colors.tertiary,
        container: theme.colors.tertiaryContainer,
        outline: theme.colors.outlineVariant,
      };
    case 'error':
      return {
        fill: theme.colors.error,
        container: theme.colors.errorContainer,
        outline: theme.colors.outlineVariant,
      };
    case 'surface':
      return {
        fill: theme.colors.primary,
        container: theme.colors.surfaceVariant,
        outline: theme.colors.outlineVariant,
      };
    case 'primary':
    default:
      return {
        fill: theme.colors.primary,
        container: theme.colors.primaryContainer,
        outline: theme.colors.outlineVariant,
      };
  }
}


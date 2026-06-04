import React from 'react';
import { Menu, useTheme } from 'react-native-paper';
import { TOKENS } from '@/theme/tokens';
import { menuStyles } from '@/theme/menuStyles';

export interface AppMenuItemProps {
  label: string;
  icon?: string;
  selected?: boolean;
  destructive?: boolean;
  disabled?: boolean;
  onPress: () => void;
}

/**
 * Shared dropdown row for Paper menus. Renders the selected-state affordance
 * (check icon + secondary-container fill + bold label) and an optional
 * destructive tint. Used by AppSelect, AppSplitButton and AppMenuButton so the
 * item look stays identical across all three.
 */
export function AppMenuItem({
  label,
  icon,
  selected = false,
  destructive = false,
  disabled = false,
  onPress,
}: AppMenuItemProps) {
  const theme = useTheme();
  const textColor = destructive
    ? theme.colors.error
    : selected
      ? theme.colors.onSecondaryContainer
      : theme.colors.onSurface;

  return (
    <Menu.Item
      title={label}
      leadingIcon={selected ? 'check' : icon}
      disabled={disabled}
      onPress={onPress}
      style={[menuStyles.menuItem, selected && { backgroundColor: theme.colors.secondaryContainer }]}
      titleStyle={{
        color: textColor,
        fontWeight: selected ? TOKENS.typography.weight.bold : TOKENS.typography.weight.medium,
      }}
    />
  );
}

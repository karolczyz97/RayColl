import React, { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Icon, Menu, Surface, Text, TouchableRipple, useTheme } from 'react-native-paper';
import { TOKENS } from '@/theme/tokens';

export interface SplitButtonOption {
  label: string;
  value: string;
  icon?: string;
}

export interface AppSplitButtonProps {
  label: string;
  onPress: () => void;
  options: SplitButtonOption[];
  onSelect: (value: string) => void;
  selectedValue?: string | null;
  leadingIcon?: string;
  disabled?: boolean;
  accessibilityLabel?: string;
  menuAccessibilityLabel?: string;
}

export function AppSplitButton({
  label,
  onPress,
  options,
  onSelect,
  selectedValue,
  leadingIcon,
  disabled = false,
  accessibilityLabel,
  menuAccessibilityLabel,
}: AppSplitButtonProps) {
  const theme = useTheme();
  const [open, setOpen] = useState(false);
  const [anchorWidth, setAnchorWidth] = useState<number | undefined>(undefined);

  const buttonColor = disabled ? theme.colors.surfaceVariant : theme.colors.secondaryContainer;
  const textColor = disabled ? theme.colors.onSurfaceVariant : theme.colors.onSecondaryContainer;
  const menuDisabled = options.length === 0;

  return (
    <Menu
      visible={open}
      onDismiss={() => setOpen(false)}
      style={[
        styles.menu,
        anchorWidth ? { width: anchorWidth, maxWidth: anchorWidth } : undefined,
        { transformOrigin: 'top' },
      ]}
      contentStyle={[
        styles.menuContent,
        { backgroundColor: theme.colors.elevation.level2 },
        anchorWidth ? { width: anchorWidth, maxWidth: anchorWidth } : undefined,
        { transformOrigin: 'top' },
      ]}
      anchor={
        <Surface
          mode="flat"
          elevation={0}
          onLayout={(event) => setAnchorWidth(event.nativeEvent.layout.width)}
          style={[styles.container, { backgroundColor: buttonColor }]}
        >
          <TouchableRipple
            disabled={disabled}
            onPress={onPress}
            style={styles.primarySegment}
            accessibilityRole="button"
            accessibilityLabel={accessibilityLabel ?? label}
            accessibilityState={{ disabled }}
          >
            <View style={styles.primaryContent}>
              {leadingIcon ? <Icon source={leadingIcon} size={TOKENS.iconSize.sm} color={textColor} /> : null}
              <Text
                variant="labelLarge"
                style={[styles.label, { color: textColor }]}
                numberOfLines={1}
              >
                {label}
              </Text>
            </View>
          </TouchableRipple>

          <View
            pointerEvents="none"
            style={[styles.divider, { backgroundColor: textColor }]}
          />

          <TouchableRipple
            disabled={menuDisabled}
            onPress={() => !menuDisabled && setOpen((current) => !current)}
            style={styles.trailingSegment}
            accessibilityRole="button"
            accessibilityLabel={menuAccessibilityLabel ?? 'Open menu'}
            accessibilityState={{ disabled: menuDisabled, expanded: open }}
          >
            <View style={styles.trailingContent}>
              <Icon source={open ? 'chevron-up' : 'chevron-down'} size={TOKENS.iconSize.md} color={textColor} />
            </View>
          </TouchableRipple>
        </Surface>
      }
    >
      {options.map((option) => {
        const isSelected = option.value === selectedValue;
        return (
          <Menu.Item
            key={option.value}
            leadingIcon={isSelected ? 'check' : option.icon}
            title={option.label}
            onPress={() => {
              onSelect(option.value);
              setOpen(false);
            }}
            style={[
              styles.menuItem,
              isSelected && { backgroundColor: theme.colors.secondaryContainer },
            ]}
            titleStyle={
              isSelected
                ? { color: theme.colors.onSecondaryContainer, fontWeight: TOKENS.typography.weight.bold }
                : { color: theme.colors.onSurface }
            }
          />
        );
      })}
    </Menu>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'stretch',
    borderRadius: TOKENS.splitButton.borderRadius,
    minHeight: TOKENS.splitButton.height,
    overflow: 'hidden',
    width: '100%',
  },
  primarySegment: {
    flex: 1,
    minHeight: TOKENS.splitButton.height,
    justifyContent: 'center',
  },
  primaryContent: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'flex-start',
    gap: TOKENS.spacing.sm,
    minHeight: TOKENS.splitButton.height,
    paddingLeft: TOKENS.spacing.md,
    paddingRight: TOKENS.spacing.sm,
  },
  label: {
    flexShrink: 1,
    fontWeight: TOKENS.typography.weight.bold,
  },
  divider: {
    alignSelf: 'center',
    height: '58%',
    opacity: 0.3,
    width: StyleSheet.hairlineWidth,
  },
  trailingSegment: {
    justifyContent: 'center',
    minHeight: TOKENS.splitButton.height,
    width: TOKENS.splitButton.trailingWidth,
  },
  trailingContent: {
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: TOKENS.splitButton.height,
    width: '100%',
  },
  menu: {
    marginTop: TOKENS.menu.gap,
  },
  menuContent: {
    borderRadius: TOKENS.radius.lg,
    overflow: 'hidden',
  },
  menuItem: {
    minHeight: TOKENS.menu.itemHeight,
    width: '100%',
    maxWidth: '100%',
  },
});

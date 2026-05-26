import React, { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { HelperText, Icon, Menu, Text, TouchableRipple, useTheme } from 'react-native-paper';
import { TOKENS } from '../theme/tokens';

export interface SelectOption {
  label: string;
  value: string;
  icon?: string;
}

export interface AppSelectProps {
  label?: string;
  value: string | null;
  options: SelectOption[];
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  helperText?: string;
  error?: boolean;
  accessibilityLabel?: string;
  testID?: string;
}

export function AppSelect({
  label,
  value,
  options,
  onChange,
  placeholder,
  disabled = false,
  helperText,
  error = false,
  accessibilityLabel,
  testID,
}: AppSelectProps) {
  const theme = useTheme();
  const [open, setOpen] = useState(false);
  const [anchorWidth, setAnchorWidth] = useState<number | undefined>(undefined);

  const selected = options.find((option) => option.value === value);
  const borderColor = error
    ? theme.colors.error
    : open
      ? theme.colors.primary
      : theme.colors.outline;
  const labelColor = error
    ? theme.colors.error
    : open
      ? theme.colors.primary
      : theme.colors.onSurfaceVariant;
  const fieldBackground = disabled ? theme.colors.surfaceDisabled : theme.colors.surface;

  return (
    <View style={styles.root} testID={testID}>
      {label ? (
        <Text variant="labelMedium" style={[styles.label, { color: labelColor }]}>
          {label}
        </Text>
      ) : null}

      <View
        onLayout={(event) => setAnchorWidth(event.nativeEvent.layout.width)}
        style={styles.anchorWrapper}
      >
        <Menu
          visible={open}
          onDismiss={() => setOpen(false)}
          style={[
            styles.menu,
            anchorWidth ? { width: anchorWidth, maxWidth: anchorWidth } : undefined,
            ({ transformOrigin: 'top' } as any),
          ]}
          contentStyle={[
            styles.menuContent,
            { backgroundColor: theme.colors.elevation.level2 },
            anchorWidth ? { width: anchorWidth, maxWidth: anchorWidth } : undefined,
            ({ transformOrigin: 'top' } as any),
          ]}
          anchor={
            <TouchableRipple
              onPress={() => !disabled && setOpen((current) => !current)}
              disabled={disabled}
              style={[
                styles.field,
                {
                  borderColor,
                  backgroundColor: fieldBackground,
                },
              ]}
              accessibilityRole="button"
              accessibilityLabel={accessibilityLabel ?? label ?? placeholder}
              accessibilityState={{ disabled, expanded: open }}
            >
              <View style={styles.fieldContent}>
                <Text
                  variant="bodyLarge"
                  style={[
                    styles.fieldText,
                    {
                      color: selected
                        ? disabled
                          ? theme.colors.onSurfaceDisabled
                          : theme.colors.onSurface
                        : theme.colors.onSurfaceVariant,
                    },
                  ]}
                  numberOfLines={1}
                >
                  {selected ? selected.label : (placeholder ?? '')}
                </Text>
                <Icon
                  source={open ? 'chevron-up' : 'chevron-down'}
                  size={22}
                  color={disabled ? theme.colors.onSurfaceDisabled : theme.colors.onSurfaceVariant}
                />
              </View>
            </TouchableRipple>
          }
        >
          {options.map((option) => {
            const isSelected = option.value === value;
            return (
              <Menu.Item
                key={option.value}
                leadingIcon={isSelected ? 'check' : option.icon}
                title={option.label}
                onPress={() => {
                  onChange(option.value);
                  setOpen(false);
                }}
                style={[
                  styles.menuItem,
                  isSelected && { backgroundColor: theme.colors.secondaryContainer },
                ]}
                titleStyle={
                  isSelected
                    ? { color: theme.colors.onSecondaryContainer, fontWeight: '700' }
                    : { color: theme.colors.onSurface }
                }
              />
            );
          })}
        </Menu>
      </View>

      {helperText ? (
        <HelperText type={error ? 'error' : 'info'} visible>
          {helperText}
        </HelperText>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    width: '100%',
  },
  label: {
    marginBottom: TOKENS.spacing.xs,
    marginLeft: TOKENS.spacing.xs,
  },
  anchorWrapper: {
    width: '100%',
  },
  field: {
    borderWidth: 1,
    borderRadius: TOKENS.control.borderRadius,
    minHeight: TOKENS.control.height,
    justifyContent: 'center',
    overflow: 'hidden',
    width: '100%',
  },
  fieldContent: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    minHeight: TOKENS.control.height,
    paddingHorizontal: TOKENS.spacing.lg,
    width: '100%',
  },
  fieldText: {
    flex: 1,
    paddingRight: TOKENS.spacing.sm,
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

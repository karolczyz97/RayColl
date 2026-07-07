import React, { useLayoutEffect, useRef } from 'react';
import { StyleSheet, View } from 'react-native';
import { HelperText, Icon, Menu, Text, TouchableRipple, useTheme } from 'react-native-paper';
import { TOKENS } from '@/theme/tokens';
import { AppMenuItem } from './AppMenuItem';
import { useMenuAnchor } from '@/hooks/useMenuAnchor';

export interface SelectOption {
  label: string;
  value: string;
  icon?: string;
  /** Nazwa sekcji; przed pierwszą opcją nowej sekcji renderuje się nagłówek. */
  section?: string;
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
  const { open, setOpen, setAnchorWidth, menuStyle, menuContentStyle } = useMenuAnchor(
    theme.colors.elevation.level2,
  );
  const anchorRef = useRef<View>(null);

  // Prime layout cache on mount so first Menu open animates from the correct position
  useLayoutEffect(() => {
    anchorRef.current?.measure(() => {});
  }, []);

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
        ref={anchorRef}
        onLayout={(event) => setAnchorWidth(event.nativeEvent.layout.width)}
        style={styles.anchorWrapper}
      >
        <Menu
          visible={open}
          onDismiss={() => setOpen(false)}
          style={menuStyle}
          contentStyle={menuContentStyle}
          anchor={
            <TouchableRipple
              onPress={() => !disabled && setOpen(true)}
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
                  size={TOKENS.iconSize.md}
                  color={disabled ? theme.colors.onSurfaceDisabled : theme.colors.onSurfaceVariant}
                />
              </View>
            </TouchableRipple>
          }
        >
          {options.map((option, index) => (
            <React.Fragment key={option.value}>
              {option.section && option.section !== options[index - 1]?.section ? (
                <Text
                  variant="labelSmall"
                  style={[styles.sectionHeader, { color: theme.colors.onSurfaceVariant }]}
                >
                  {option.section}
                </Text>
              ) : null}
              <AppMenuItem
                label={option.label}
                icon={option.icon}
                selected={option.value === value}
                onPress={() => {
                  onChange(option.value);
                  setOpen(false);
                }}
              />
            </React.Fragment>
          ))}
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
  sectionHeader: {
    paddingHorizontal: TOKENS.spacing.lg,
    paddingTop: TOKENS.spacing.sm,
    paddingBottom: TOKENS.spacing.xs,
  },
});

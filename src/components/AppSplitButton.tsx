import React from 'react';
import { StyleSheet, View } from 'react-native';
import Animated from 'react-native-reanimated';
import { Icon, Menu, Text, TouchableRipple, useTheme } from 'react-native-paper';
import { TOKENS } from '@/theme/tokens';
import { usePressAnimation } from '@/hooks/usePressAnimation';
import { AppMenuItem } from './AppMenuItem';
import { useMenuAnchor } from '@/hooks/useMenuAnchor';

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
  const { open, setOpen, setAnchorWidth, menuStyle, menuContentStyle } = useMenuAnchor(
    theme.colors.elevation.level2,
  );
  const { animatedStyle, onPressIn, onPressOut } = usePressAnimation();

  const buttonColor = disabled ? theme.colors.surfaceVariant : theme.colors.secondaryContainer;
  const textColor = disabled ? theme.colors.onSurfaceVariant : theme.colors.onSecondaryContainer;
  const menuDisabled = options.length === 0;

  return (
    <Menu
      visible={open}
      onDismiss={() => setOpen(false)}
      style={menuStyle}
      contentStyle={menuContentStyle}
      anchor={
        <Animated.View
          onLayout={(event) => setAnchorWidth(event.nativeEvent.layout.width)}
          style={[styles.container, { backgroundColor: buttonColor }, animatedStyle]}
        >
          <TouchableRipple
            disabled={disabled}
            onPress={onPress}
            onPressIn={onPressIn}
            onPressOut={onPressOut}
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
            onPressIn={onPressIn}
            onPressOut={onPressOut}
            style={styles.trailingSegment}
            accessibilityRole="button"
            accessibilityLabel={menuAccessibilityLabel ?? 'Open menu'}
            accessibilityState={{ disabled: menuDisabled, expanded: open }}
          >
            <View style={styles.trailingContent}>
              <Icon source={open ? 'chevron-up' : 'chevron-down'} size={TOKENS.iconSize.md} color={textColor} />
            </View>
          </TouchableRipple>
        </Animated.View>
      }
    >
      {options.map((option) => (
        <AppMenuItem
          key={option.value}
          label={option.label}
          icon={option.icon}
          selected={option.value === selectedValue}
          onPress={() => {
            onSelect(option.value);
            setOpen(false);
          }}
        />
      ))}
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
});

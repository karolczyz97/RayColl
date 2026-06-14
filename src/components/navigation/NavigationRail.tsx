import React from 'react';
import { StyleSheet, View, type ViewStyle } from 'react-native';
import Animated, {
  useAnimatedStyle,
  withTiming,
  type AnimatedStyle,
} from 'react-native-reanimated';
import {
  Icon,
  IconButton,
  Text,
  TouchableRipple,
  useTheme,
} from 'react-native-paper';

import { useI18n } from '@/i18n';
import { TOKENS } from '@/theme/tokens';
import type { FlashcardStoreState } from '@/store/FlashcardStoreTypes';
import {
  NAVIGATION_DESTINATIONS,
  type NavigationDestination,
  type TopLevelDestinationKey,
} from './navigationDestinations';
import { NavigationAccountMenu } from './NavigationAccountMenu';

interface NavigationRailProps {
  expanded: boolean;
  activeDestination: TopLevelDestinationKey | null;
  user: FlashcardStoreState['user'];
  onNavigate: (destination: NavigationDestination) => void;
  onLogin: () => void;
  onLogout: () => void;
  onToggleExpanded: () => void;
}

interface RailItemProps {
  focusedIcon: string;
  unfocusedIcon: string;
  label: string;
  active: boolean;
  onPress: () => void;
  /** Animated opacity style for the label, shared across items. */
  labelStyle: AnimatedStyle<ViewStyle>;
}

/**
 * A single rail destination. The icon lives in a fixed leading box the width of
 * the collapsed rail, so it is centred when collapsed and keeps that exact x
 * when expanded — the icons never slide, only the labels fade.
 */
function RailItem({
  focusedIcon,
  unfocusedIcon,
  label,
  active,
  onPress,
  labelStyle,
}: RailItemProps) {
  const theme = useTheme();
  const icon = active ? focusedIcon : unfocusedIcon;
  const iconColor = active ? theme.colors.onSecondaryContainer : theme.colors.onSurfaceVariant;
  const labelColor = active ? theme.colors.onSurface : theme.colors.onSurfaceVariant;

  return (
    <TouchableRipple
      borderless
      onPress={onPress}
      style={styles.itemRipple}
      accessibilityRole="button"
      accessibilityState={{ selected: active }}
      accessibilityLabel={label}
    >
      <View style={styles.itemRow}>
        <View style={styles.leading}>
          <View
            style={[
              styles.itemIndicator,
              { backgroundColor: active ? theme.colors.secondaryContainer : 'transparent' },
            ]}
          >
            <Icon source={icon} size={TOKENS.iconSize.md} color={iconColor} />
          </View>
        </View>
        <Animated.View style={[styles.labelWrap, labelStyle]} pointerEvents="none">
          <Text variant="labelLarge" numberOfLines={1} style={{ color: labelColor }}>
            {label}
          </Text>
        </Animated.View>
      </View>
    </TouchableRipple>
  );
}

export function NavigationRail({
  expanded,
  activeDestination,
  user,
  onNavigate,
  onLogin,
  onLogout,
  onToggleExpanded,
}: NavigationRailProps) {
  const { t } = useI18n();
  const theme = useTheme();

  // Animate the collapse/expand width change instead of snapping. The sibling
  // content area follows via flex, so this drives the whole reflow smoothly.
  const animatedWidthStyle = useAnimatedStyle(() => ({
    width: withTiming(expanded ? TOKENS.layout.expandedRailWidth : TOKENS.layout.railWidth, {
      duration: TOKENS.motion.duration.medium,
    }),
  }));

  // Labels fade rather than relayout, so collapsing reads as "text disappears"
  // instead of "icons slide to the centre".
  const labelAnimatedStyle = useAnimatedStyle(() => ({
    opacity: withTiming(expanded ? 1 : 0, { duration: TOKENS.motion.duration.short }),
  }));

  return (
    <Animated.View
      style={[
        styles.root,
        {
          backgroundColor: theme.colors.surface,
          borderRightColor: theme.colors.outlineVariant,
        },
        animatedWidthStyle,
      ]}
    >
      <View style={styles.toggleRow}>
        <View style={styles.leading}>
          <IconButton
            icon="menu"
            size={TOKENS.iconSize.md}
            onPress={onToggleExpanded}
            accessibilityLabel={expanded ? 'Collapse navigation' : 'Expand navigation'}
            style={styles.toggleButton}
          />
        </View>
      </View>

      <View style={styles.destinations}>
        {NAVIGATION_DESTINATIONS.map((destination) => {
          const label = t(destination.labelKey);
          const active = activeDestination === destination.key;

          return (
            <RailItem
              key={destination.key}
              focusedIcon={destination.focusedIcon}
              unfocusedIcon={destination.unfocusedIcon}
              label={label}
              active={active}
              onPress={() => onNavigate(destination)}
              labelStyle={labelAnimatedStyle}
            />
          );
        })}
      </View>

      <View style={styles.footer}>
        <NavigationAccountMenu
          user={user}
          expanded={expanded}
          onLogin={onLogin}
          onLogout={onLogout}
        />
      </View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  root: {
    flexShrink: 0,
    overflow: 'hidden',
    borderRightWidth: StyleSheet.hairlineWidth,
    paddingVertical: TOKENS.spacing.md,
  },
  // Fixed leading column = collapsed rail width. Centres icons and the menu
  // on the collapsed rail's centre line and pins that x across expand/collapse.
  leading: {
    width: TOKENS.layout.railWidth,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  toggleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginBottom: TOKENS.spacing.xs,
  },
  toggleButton: {
    margin: 0,
  },
  destinations: {
    flex: 1,
    gap: TOKENS.spacing.xs,
  },
  itemRipple: {
    alignSelf: 'stretch',
    minHeight: TOKENS.touchTarget.min,
    borderRadius: TOKENS.radius.lg,
    overflow: 'hidden',
  },
  itemRow: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: TOKENS.touchTarget.min,
  },
  itemIndicator: {
    width: TOKENS.touchTarget.compact,
    height: TOKENS.touchTarget.compact,
    borderRadius: TOKENS.radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  labelWrap: {
    flex: 1,
    paddingRight: TOKENS.spacing.md,
  },
  footer: {
    gap: TOKENS.spacing.md,
    alignItems: 'stretch',
  },
});

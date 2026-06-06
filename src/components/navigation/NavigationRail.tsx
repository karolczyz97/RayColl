import React from 'react';
import { StyleSheet, View, type ViewStyle } from 'react-native';
import Animated, {
  useAnimatedStyle,
  withTiming,
  type AnimatedStyle,
} from 'react-native-reanimated';
import {
  Avatar,
  Icon,
  IconButton,
  Text,
  TouchableRipple,
  useTheme,
} from 'react-native-paper';

import { useI18n } from '@/i18n';
import { TOKENS } from '@/theme/tokens';
import { APP_NAME } from '@/constants/app';
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
 * A single rail destination. The icon is left-anchored at a fixed inset so it
 * stays put when the rail collapses/expands; only the label fades (and the rail
 * width animates), avoiding the icons jumping toward the centre.
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
        <View
          style={[
            styles.itemIndicator,
            { backgroundColor: active ? theme.colors.secondaryContainer : 'transparent' },
          ]}
        >
          <Icon source={icon} size={TOKENS.iconSize.md} color={iconColor} />
        </View>
        <Animated.View style={[styles.itemLabelWrap, labelStyle]} pointerEvents="none">
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
        <IconButton
          icon="menu"
          size={TOKENS.iconSize.md}
          onPress={onToggleExpanded}
          accessibilityLabel={expanded ? 'Collapse navigation' : 'Expand navigation'}
          style={styles.toggleButton}
        />
      </View>

      <View style={styles.brand}>
        <Avatar.Icon
          icon="book-open-page-variant"
          size={TOKENS.touchTarget.compact}
          style={{ backgroundColor: theme.colors.secondaryContainer }}
          color={theme.colors.onSecondaryContainer}
          accessibilityLabel={`${APP_NAME} Logo Icon`}
        />
        <Animated.View style={[styles.brandTextWrap, labelAnimatedStyle]} pointerEvents="none">
          <Text variant="titleLarge" numberOfLines={1} style={styles.brandText}>
            {APP_NAME}
          </Text>
        </Animated.View>
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

// Fixed left inset shared by the hamburger, brand logo, and destination icons so
// nothing shifts horizontally between collapsed and expanded states.
const ITEM_INSET = TOKENS.spacing.sm;

const styles = StyleSheet.create({
  root: {
    flexShrink: 0,
    overflow: 'hidden',
    borderRightWidth: StyleSheet.hairlineWidth,
    paddingVertical: TOKENS.spacing.md,
  },
  toggleRow: {
    alignItems: 'flex-start',
    paddingHorizontal: TOKENS.spacing.xs,
    marginBottom: TOKENS.spacing.xs,
  },
  toggleButton: {
    margin: 0,
  },
  brand: {
    flexDirection: 'row',
    alignItems: 'center',
    minHeight: TOKENS.layout.collapsedRailBrandMinHeight,
    marginBottom: TOKENS.spacing.lg,
    paddingLeft: ITEM_INSET,
    gap: TOKENS.spacing.sm,
  },
  brandTextWrap: {
    flex: 1,
  },
  brandText: {
    fontWeight: TOKENS.typography.weight.bold,
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
    paddingLeft: ITEM_INSET,
    gap: TOKENS.spacing.sm,
  },
  itemIndicator: {
    width: TOKENS.touchTarget.compact,
    height: TOKENS.touchTarget.compact,
    borderRadius: TOKENS.radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  itemLabelWrap: {
    flex: 1,
  },
  footer: {
    gap: TOKENS.spacing.md,
    alignItems: 'stretch',
  },
});

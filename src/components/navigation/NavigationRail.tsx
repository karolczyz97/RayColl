import React, { useCallback, useEffect, useRef } from 'react';
import { StyleSheet, View, type LayoutChangeEvent, type ViewStyle } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
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
import { getRailIndicatorY, type RailItemLayout } from './navigationRailUtils';

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
  /** Reports the leading column layout so the parent can position the shared indicator. */
  onLeadingLayout?: (event: LayoutChangeEvent) => void;
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
  onLeadingLayout,
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
        <View style={styles.leading} onLayout={onLeadingLayout}>
          <View style={styles.itemIndicator}>
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

  const itemLayoutsRef = useRef<RailItemLayout[]>([]);
  const indicatorY = useSharedValue(-1);
  const indicatorOpacity = useSharedValue(0);

  const activeIndex = NAVIGATION_DESTINATIONS.findIndex((d) => d.key === activeDestination);
  const activeIndexRef = useRef(activeIndex);

  // Animate the shared indicator pill to the active item on every navigation.
  useEffect(() => {
    activeIndexRef.current = activeIndex;
    const targetY = getRailIndicatorY(activeIndex, itemLayoutsRef.current);
    if (targetY !== null) {
      indicatorY.value = withSpring(targetY, TOKENS.motion.spring.tap);
      indicatorOpacity.value = withTiming(1, { duration: TOKENS.motion.duration.short });
    } else {
      indicatorOpacity.value = withTiming(0, { duration: TOKENS.motion.duration.short });
    }
  }, [activeIndex, indicatorY, indicatorOpacity]);

  // Re-apply the correct position when layout measurements arrive later.
  const handleLeadingLayout = useCallback(
    (index: number) => (event: LayoutChangeEvent) => {
      const { y, height } = event.nativeEvent.layout;
      itemLayoutsRef.current[index] = { y, height };
      if (index === activeIndexRef.current) {
        const targetY = getRailIndicatorY(index, itemLayoutsRef.current);
        if (targetY !== null) {
          indicatorY.value = targetY;
          indicatorOpacity.value = 1;
        }
      }
    },
    [indicatorY, indicatorOpacity],
  );

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

  const indicatorAnimatedStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: indicatorY.value }],
    opacity: indicatorOpacity.value,
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
        <Animated.View
          pointerEvents="none"
          style={[
            styles.activeIndicator,
            { backgroundColor: theme.colors.secondaryContainer },
            indicatorAnimatedStyle,
          ]}
        />
        {NAVIGATION_DESTINATIONS.map((destination, index) => {
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
              onLeadingLayout={handleLeadingLayout(index)}
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
    position: 'relative',
  },
  activeIndicator: {
    position: 'absolute',
    left: (TOKENS.layout.railWidth - TOKENS.touchTarget.compact) / 2,
    width: TOKENS.touchTarget.compact,
    height: TOKENS.touchTarget.compact,
    borderRadius: TOKENS.radius.pill,
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

import React from 'react';
import { StyleSheet, View } from 'react-native';
import Animated, { useAnimatedStyle, withTiming } from 'react-native-reanimated';
import {
  Avatar,
  Drawer,
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

interface CollapsedRailItemProps {
  focusedIcon: string;
  unfocusedIcon: string;
  label: string;
  active: boolean;
  onPress: () => void;
}

function CollapsedRailItem({
  focusedIcon,
  unfocusedIcon,
  label,
  active,
  onPress,
}: CollapsedRailItemProps) {
  const theme = useTheme();
  const icon = active ? focusedIcon : unfocusedIcon;
  const iconColor = active ? theme.colors.onSecondaryContainer : theme.colors.onSurfaceVariant;

  return (
    <TouchableRipple
      borderless
      onPress={onPress}
      style={styles.collapsedItemRipple}
      accessibilityRole="button"
      accessibilityState={{ selected: active }}
      accessibilityLabel={label}
    >
      <View
        style={[
          styles.collapsedIconContainer,
          {
            backgroundColor: active ? theme.colors.secondaryContainer : 'transparent',
          },
        ]}
      >
        <Icon source={icon} size={TOKENS.iconSize.md} color={iconColor} />
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
      <View style={[styles.toggleRow, expanded ? styles.toggleRowExpanded : styles.toggleRowCollapsed]}>
        <IconButton
          icon="menu"
          size={TOKENS.iconSize.md}
          onPress={onToggleExpanded}
          accessibilityLabel={expanded ? 'Collapse navigation' : 'Expand navigation'}
          style={styles.toggleButton}
        />
      </View>

      <View style={[styles.brand, expanded ? styles.brandExpanded : styles.brandCollapsed]}>
        <Avatar.Icon
          icon="book-open-page-variant"
          size={expanded ? 44 : 40}
          style={{ backgroundColor: theme.colors.secondaryContainer }}
          color={theme.colors.onSecondaryContainer}
          accessibilityLabel={`${APP_NAME} Logo Icon`}
        />
        {expanded ? (
          <Text variant="titleLarge" style={styles.brandText}>
            {APP_NAME}
          </Text>
        ) : null}
      </View>

      <View style={styles.destinations}>
        {NAVIGATION_DESTINATIONS.map((destination) => {
          const label = t(destination.labelKey);
          const active = activeDestination === destination.key;

          if (expanded) {
            return (
              <Drawer.Item
                key={destination.key}
                icon={active ? destination.focusedIcon : destination.unfocusedIcon}
                label={label}
                active={active}
                onPress={() => onNavigate(destination)}
                accessibilityLabel={label}
                style={styles.expandedItem}
              />
            );
          }

          return (
            <CollapsedRailItem
              key={destination.key}
              focusedIcon={destination.focusedIcon}
              unfocusedIcon={destination.unfocusedIcon}
              label={label}
              active={active}
              onPress={() => onNavigate(destination)}
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
  toggleRow: {
    marginBottom: TOKENS.spacing.xs,
  },
  toggleRowExpanded: {
    alignItems: 'flex-start',
    paddingHorizontal: TOKENS.spacing.sm,
  },
  toggleRowCollapsed: {
    alignItems: 'center',
  },
  toggleButton: {
    margin: 0,
  },
  brand: {
    minHeight: TOKENS.layout.collapsedRailBrandMinHeight,
    marginBottom: TOKENS.spacing.lg,
  },
  brandCollapsed: {
    alignItems: 'center',
    justifyContent: 'center',
    gap: TOKENS.spacing.xs,
  },
  brandExpanded: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: TOKENS.spacing.sm,
    paddingHorizontal: TOKENS.spacing.lg,
  },
  brandText: {
    fontWeight: TOKENS.typography.weight.bold,
    flex: 1,
  },
  destinations: {
    flex: 1,
    gap: TOKENS.spacing.xs,
  },
  collapsedItemRipple: {
    alignSelf: 'center',
    width: TOKENS.layout.collapsedRailItemWidth,
    minHeight: TOKENS.layout.collapsedRailItemMinHeight,
    borderRadius: TOKENS.radius.lg,
    overflow: 'hidden',
    alignItems: 'center',
    justifyContent: 'center',
  },
  collapsedIconContainer: {
    width: TOKENS.layout.collapsedRailIconContainerWidth,
    height: TOKENS.layout.collapsedRailIconContainerHeight,
    borderRadius: TOKENS.radius.pill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  expandedItem: {
    marginHorizontal: TOKENS.spacing.sm,
  },
  footer: {
    gap: TOKENS.spacing.md,
    alignItems: 'stretch',
  },
});

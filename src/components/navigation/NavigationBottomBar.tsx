import React from 'react';
import { Pressable, StyleSheet, type PressableProps } from 'react-native';
import { BottomNavigation, useTheme } from 'react-native-paper';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { useI18n } from '../../i18n';
import { TOKENS } from '../../theme/tokens';
import {
  NAVIGATION_DESTINATIONS,
  type NavigationDestination,
  type TopLevelDestinationKey,
} from './navigationDestinations';

type BottomBarRoute = NavigationDestination & {
  title: string;
  accessibilityLabel: string;
  testID: string;
};

interface NavigationBottomBarProps {
  activeDestination: TopLevelDestinationKey;
  onNavigate: (destination: NavigationDestination) => void;
}

export function NavigationBottomBar({
  activeDestination,
  onNavigate,
}: NavigationBottomBarProps) {
  const { t } = useI18n();
  const theme = useTheme();
  const insets = useSafeAreaInsets();

  const routes = React.useMemo<BottomBarRoute[]>(
    () =>
      NAVIGATION_DESTINATIONS.map((destination) => {
        const label = t(destination.labelKey);

        return {
          ...destination,
          title: label,
          accessibilityLabel: label,
          testID: `bottom-nav-${destination.key}`,
        };
      }),
    [t],
  );
  const activeIndex = Math.max(
    0,
    routes.findIndex((route) => route.key === activeDestination),
  );

  return (
    <BottomNavigation.Bar
      navigationState={{ index: activeIndex, routes }}
      onTabPress={({ route }) => onNavigate(route)}
      labeled
      shifting={false}
      keyboardHidesNavigationBar
      renderTouchable={({
        key,
        children,
        accessibilityLabel,
        accessibilityRole,
        accessibilityState,
        onLongPress,
        onPress,
        style,
        testID,
      }) => (
        <Pressable
          key={key}
          accessibilityLabel={accessibilityLabel}
          accessibilityRole={accessibilityRole}
          accessibilityState={accessibilityState}
          onLongPress={onLongPress}
          onPress={onPress}
          style={style as PressableProps['style']}
          testID={testID}
        >
          {children}
        </Pressable>
      )}
      safeAreaInsets={{ bottom: insets.bottom }}
      activeColor={theme.colors.onSecondaryContainer}
      inactiveColor={theme.colors.onSurfaceVariant}
      activeIndicatorStyle={{ backgroundColor: theme.colors.secondaryContainer }}
      style={[
        styles.bar,
        {
          backgroundColor: theme.colors.surface,
          borderTopColor: theme.colors.outlineVariant,
        },
      ]}
      testID="bottom-navigation-bar"
    />
  );
}

const styles = StyleSheet.create({
  bar: {
    borderTopWidth: StyleSheet.hairlineWidth,
    flexShrink: 0,
    minHeight: TOKENS.layout.bottomBarHeight,
  },
});

import React, { useCallback } from 'react';
import { StyleSheet, View } from 'react-native';
import { router, usePathname } from 'expo-router';
import { useTheme } from 'react-native-paper';

import { useResponsiveLayout } from '@/hooks/useResponsiveLayout';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { useAppTheme } from '@/contexts/UserPreferencesContext';
import { TOKENS } from '@/theme/tokens';
import { NavigationShellProvider } from '@/contexts/NavigationShellContext';
import { NavigationRail } from './NavigationRail';
import {
  getActiveDestination,
  type NavigationDestination,
} from './navigationDestinations';
import {
  NavigationBlockerProvider,
  useNavigationBlockerState,
} from '@/contexts/NavigationBlockerContext';
import type { Href } from 'expo-router';

export function AppNavigationShell({ children }: { children: React.ReactNode }) {
  return (
    <NavigationBlockerProvider>
      <AppNavigationShellContent>{children}</AppNavigationShellContent>
    </NavigationBlockerProvider>
  );
}

function AppNavigationShellContent({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const theme = useTheme();
  const responsive = useResponsiveLayout();
  const { user, signIn, signOut } = useFlashcardStore();
  const { railExpanded, setRailExpanded } = useAppTheme();
  const { isNavigationBlocked, requestBlockedNavigation } = useNavigationBlockerState();

  // The rail can no longer be fully hidden — it is always shown on medium+
  // widths, only collapsed or expanded.
  const showPersistentNavigation = responsive.showPersistentNavigation;
  const isRailExpanded = showPersistentNavigation && railExpanded;
  const actualNavWidth = showPersistentNavigation
    ? isRailExpanded
      ? TOKENS.layout.expandedRailWidth
      : TOKENS.layout.railWidth
    : 0;
  const contentWidth = Math.max(0, responsive.width - actualNavWidth);
  const activeDestination = getActiveDestination(pathname);

  const contextValue = React.useMemo(
    () => ({
      isCompact: responsive.isCompact,
      isMedium: responsive.isMedium,
      isExpanded: responsive.isExpanded,
      contentWidth,
      showPersistentNavigation,
      navWidth: actualNavWidth,
    }),
    [
      actualNavWidth,
      contentWidth,
      responsive.isCompact,
      responsive.isExpanded,
      responsive.isMedium,
      showPersistentNavigation,
    ],
  );

  const navigateTo = useCallback((href: Href) => {
    router.navigate(href);
  }, []);

  const handleNavigate = useCallback((destination: NavigationDestination) => {
    const navigate = () => navigateTo(destination.href);
    if (isNavigationBlocked) {
      if (!requestBlockedNavigation(navigate)) {
        console.warn('Navigation blocker is active without a leave handler.');
      }
      return;
    }

    navigate();
  }, [isNavigationBlocked, navigateTo, requestBlockedNavigation]);

  const handleLogin = React.useCallback(() => {
    void signIn();
  }, [signIn]);

  const handleLogout = React.useCallback(() => {
    void signOut();
  }, [signOut]);

  if (!showPersistentNavigation) {
    return (
      <NavigationShellProvider value={contextValue}>{children}</NavigationShellProvider>
    );
  }

  return (
    <NavigationShellProvider value={contextValue}>
      <View style={[styles.root, { backgroundColor: theme.colors.background }]}>
        <NavigationRail
          expanded={isRailExpanded}
          activeDestination={activeDestination}
          user={user}
          onNavigate={handleNavigate}
          onLogin={handleLogin}
          onLogout={handleLogout}
          onToggleExpanded={() => void setRailExpanded(!railExpanded)}
        />
        <View style={styles.content}>{children}</View>
      </View>
    </NavigationShellProvider>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    flexDirection: 'row',
    minHeight: 0,
    minWidth: 0,
  },
  content: {
    flex: 1,
    minHeight: 0,
    minWidth: 0,
  },
});

import React from 'react';
import { StyleSheet, View } from 'react-native';
import { router, usePathname } from 'expo-router';
import { useTheme } from 'react-native-paper';

import { useResponsiveLayout } from '../../hooks/useResponsiveLayout';
import { useFlashcardStore } from '../../hooks/useFlashcardStore';
import { useAppTheme } from '../../contexts/ThemeContext';
import { TOKENS } from '../../theme/tokens';
import { NavigationShellProvider } from '../../contexts/NavigationShellContext';
import { NavigationBottomBar } from './NavigationBottomBar';
import { NavigationRail } from './NavigationRail';
import {
  IMPORT_ACTION_HREF,
  getActiveDestination,
  isImmersivePathname,
  type NavigationDestination,
} from './navigationDestinations';

/**
 * On >=600px the persistent navigation rail is shown or hidden purely by user
 * preference (`railVisible`), persisted across reloads — it never auto-hides on
 * route changes. On <600px there is no rail; screens keep their own top bar.
 * When the rail is hidden, `AppTopBar` shows a leading menu button to reveal it.
 */
export function AppNavigationShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const theme = useTheme();
  const responsive = useResponsiveLayout();
  const { user, signIn, signOut } = useFlashcardStore();
  const { railVisible, setRailVisible, railExpanded, setRailExpanded } = useAppTheme();

  const canShowPersistentNavigation = responsive.showPersistentNavigation; // width >= 600
  const showPersistentNavigation = canShowPersistentNavigation && railVisible;
  const isRailExpanded = showPersistentNavigation && railExpanded;
  const actualNavWidth = showPersistentNavigation
    ? isRailExpanded
      ? TOKENS.layout.expandedRailWidth
      : TOKENS.layout.railWidth
    : 0;
  const contentWidth = Math.max(0, responsive.width - actualNavWidth);
  const activeDestination = getActiveDestination(pathname);
  const showBottomNavigation =
    responsive.isCompact && activeDestination !== null && !isImmersivePathname(pathname);

  const contextValue = React.useMemo(
    () => ({
      isCompact: responsive.isCompact,
      isMedium: responsive.isMedium,
      isExpanded: responsive.isExpanded,
      contentWidth,
      showPersistentNavigation,
      showBottomNavigation,
      navWidth: actualNavWidth,
    }),
    [
      actualNavWidth,
      contentWidth,
      responsive.isCompact,
      responsive.isExpanded,
      responsive.isMedium,
      showBottomNavigation,
      showPersistentNavigation,
    ],
  );

  const handleNavigate = React.useCallback((destination: NavigationDestination) => {
    router.navigate(destination.href);
  }, []);

  const handleImport = React.useCallback(() => {
    router.push(IMPORT_ACTION_HREF);
  }, []);

  const handleLogin = React.useCallback(() => {
    void signIn();
  }, [signIn]);

  const handleLogout = React.useCallback(() => {
    void signOut();
  }, [signOut]);

  if (!showPersistentNavigation) {
    if (showBottomNavigation && activeDestination) {
      return (
        <NavigationShellProvider value={contextValue}>
          <View style={[styles.bottomRoot, { backgroundColor: theme.colors.background }]}>
            <View style={styles.content}>{children}</View>
            <NavigationBottomBar
              activeDestination={activeDestination}
              onNavigate={handleNavigate}
            />
          </View>
        </NavigationShellProvider>
      );
    }

    return <NavigationShellProvider value={contextValue}>{children}</NavigationShellProvider>;
  }

  return (
    <NavigationShellProvider value={contextValue}>
      <View style={[styles.root, { backgroundColor: theme.colors.background }]}>
        <NavigationRail
          expanded={isRailExpanded}
          activeDestination={activeDestination}
          user={user}
          onNavigate={handleNavigate}
          onImport={handleImport}
          onLogin={handleLogin}
          onLogout={handleLogout}
          onToggleExpanded={() => void setRailExpanded(!railExpanded)}
          onHide={() => void setRailVisible(false)}
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
  bottomRoot: {
    flex: 1,
    minHeight: 0,
    minWidth: 0,
  },
});

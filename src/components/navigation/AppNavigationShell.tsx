import React, { useCallback, useRef, useState } from 'react';
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
  StudyNavigationGuardProvider,
  type StudyExitHandler,
} from '@/features/study/StudyNavigationGuardContext';
import type { Href } from 'expo-router';

export function AppNavigationShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const theme = useTheme();
  const responsive = useResponsiveLayout();
  const { user, signIn, signOut } = useFlashcardStore();
  const { railExpanded, setRailExpanded } = useAppTheme();

  const [isStudyActive, setStudyActive] = useState(false);
  const studyExitHandlerRef = useRef<StudyExitHandler | null>(null);

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

  const setStudyExitHandler = useCallback((handler: StudyExitHandler | null) => {
    studyExitHandlerRef.current = handler;
  }, []);

  const requestStudyExit = useCallback((navigate: () => void) => {
    const handler = studyExitHandlerRef.current;
    if (!handler) {
      return false;
    }

    handler(navigate);
    return true;
  }, []);

  const navigateTo = useCallback((href: Href) => {
    router.navigate(href);
  }, []);

  const handleNavigate = useCallback((destination: NavigationDestination) => {
    const navigate = () => navigateTo(destination.href);
    if (isStudyActive) {
      if (!requestStudyExit(navigate)) {
        console.warn('Study navigation guard is active without an exit handler.');
      }
      return;
    }

    navigate();
  }, [isStudyActive, navigateTo, requestStudyExit]);

  const handleLogin = React.useCallback(() => {
    void signIn();
  }, [signIn]);

  const handleLogout = React.useCallback(() => {
    void signOut();
  }, [signOut]);

  const studyGuardValue = React.useMemo(
    () => ({
      isStudyActive,
      requestStudyExit,
      setStudyActive,
      setStudyExitHandler,
    }),
    [isStudyActive, requestStudyExit, setStudyExitHandler],
  );

  if (!showPersistentNavigation) {
    return (
      <StudyNavigationGuardProvider value={studyGuardValue}>
        <NavigationShellProvider value={contextValue}>{children}</NavigationShellProvider>
      </StudyNavigationGuardProvider>
    );
  }

  return (
    <StudyNavigationGuardProvider value={studyGuardValue}>
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
    </StudyNavigationGuardProvider>
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

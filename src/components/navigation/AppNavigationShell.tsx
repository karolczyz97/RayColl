import React, { useCallback, useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { router, usePathname } from 'expo-router';
import { useTheme } from 'react-native-paper';

import { useResponsiveLayout } from '@/hooks/useResponsiveLayout';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { useAppTheme } from '@/contexts/UserPreferencesContext';
import { useI18n } from '@/i18n';
import { TOKENS } from '@/theme/tokens';
import { NavigationShellProvider } from '@/contexts/NavigationShellContext';
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog';
import { NavigationRail } from './NavigationRail';
import {
  getActiveDestination,
  type NavigationDestination,
} from './navigationDestinations';
import { StudyNavigationGuardProvider } from '@/features/study/StudyNavigationGuardContext';

export function AppNavigationShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const theme = useTheme();
  const { t } = useI18n();
  const responsive = useResponsiveLayout();
  const { user, signIn, signOut } = useFlashcardStore();
  const { railExpanded, setRailExpanded } = useAppTheme();

  const [showRailGuard, setShowRailGuard] = useState(false);
  const [isStudyActive, setStudyActive] = useState(false);
  const pendingNavRef = useRef<NavigationDestination | null>(null);

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

  const handleNavigate = useCallback((destination: NavigationDestination) => {
    if (isStudyActive) {
      pendingNavRef.current = destination;
      setShowRailGuard(true);
      return;
    }
    router.navigate(destination.href);
  }, [isStudyActive]);

  const confirmRailNavAway = useCallback(() => {
    setShowRailGuard(false);
    if (pendingNavRef.current) {
      router.navigate(pendingNavRef.current.href);
      pendingNavRef.current = null;
    }
  }, []);

  const cancelRailNavAway = useCallback(() => {
    setShowRailGuard(false);
    pendingNavRef.current = null;
  }, []);

  const handleLogin = React.useCallback(() => {
    void signIn();
  }, [signIn]);

  const handleLogout = React.useCallback(() => {
    void signOut();
  }, [signOut]);

  const studyGuardValue = React.useMemo(
    () => ({ isStudyActive, setStudyActive }),
    [isStudyActive],
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

        <ConfirmDialog
          visible={showRailGuard}
          title={t('study.exit_confirm_title')}
          message={t('study.exit_confirm_message')}
          confirmLabel={t('study.exit_confirm_btn')}
          cancelLabel={t('btn.cancel')}
          onConfirm={confirmRailNavAway}
          onDismiss={cancelRailNavAway}
        />
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

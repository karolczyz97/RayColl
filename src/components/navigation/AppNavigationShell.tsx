import React, { useCallback, useRef, useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { router, usePathname } from 'expo-router';
import { useTheme } from 'react-native-paper';

import { useResponsiveLayout } from '@/hooks/useResponsiveLayout';
import { useFlashcardStore } from '@/hooks/useFlashcardStore';
import { useAppTheme } from '@/contexts/ThemeContext';
import { useI18n } from '@/i18n';
import { TOKENS } from '@/theme/tokens';
import { NavigationShellProvider } from '@/contexts/NavigationShellContext';
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog';
import { NavigationRail } from './NavigationRail';
import {
  IMPORT_ACTION_HREF,
  getActiveDestination,
  type NavigationDestination,
} from './navigationDestinations';
import { getIsStudyActive } from '@/features/study/studyGuard';

export function AppNavigationShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const theme = useTheme();
  const { t } = useI18n();
  const responsive = useResponsiveLayout();
  const { user, signIn, signOut } = useFlashcardStore();
  const { railVisible, setRailVisible, railExpanded, setRailExpanded } = useAppTheme();

  const [showRailGuard, setShowRailGuard] = useState(false);
  const pendingNavRef = useRef<NavigationDestination | null>(null);

  const canShowPersistentNavigation = responsive.showPersistentNavigation;
  const showPersistentNavigation = canShowPersistentNavigation && railVisible;
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
    if (getIsStudyActive()) {
      pendingNavRef.current = destination;
      setShowRailGuard(true);
      return;
    }
    router.navigate(destination.href);
  }, []);

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

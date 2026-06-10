import React, { useMemo } from 'react';
import { StyleSheet } from 'react-native';
import Animated, { ZoomIn } from 'react-native-reanimated';
import { router } from 'expo-router';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { DashboardBrand, DashboardActions } from '@/components/dashboard/DashboardBarContent';
import { DashboardStats } from '@/components/dashboard/DashboardStats';
import { EmptyDashboardState } from '@/components/dashboard/EmptyDashboardState';
import { DeckGrid } from '@/components/dashboard/DeckGrid';
import { computeStreak, getTotalCardsCount, getTotalDueCardsCount } from '@/store/selectors/stats';
import { useResponsiveLayout } from '@/hooks/useResponsiveLayout';
import { useNavigationShell } from '@/contexts/NavigationShellContext';
import { TOKENS, getTokenMotionEnterDelay } from '@/theme/tokens';
import { AppScreen } from '@/components/layout/AppScreen';
import { AnimatedSection } from '@/components/layout/AnimatedSection';
import { LoadingState } from '@/components/layout/LoadingState';
import { SyncStatusBanner } from '@/components/feedback/SyncStatusBanner';
import { AppSnackbar } from '@/components/feedback/AppSnackbar';
import { useI18n } from '@/i18n';
import { ROUTES } from '@/constants/routes';
import { AppFloatingActionButton } from '@/components/AppFloatingActionButton';

export default function Dashboard() {
  const store = useFlashcardStore();
  const { contentMaxWidth } = useResponsiveLayout();
  const { showPersistentNavigation } = useNavigationShell();
  const { t } = useI18n();

  const { groups, studyModes, getDueCards, user, signIn, signOut, isLoading, activityHeatmap, lastLoginError, clearLastLoginError } = store;

  const handleLogin = async () => {
    try {
      await signIn();
    } catch {
      // Error already captured in store.lastLoginError
    }
  };

  const handleLogout = async () => {
    try {
      await signOut();
    } catch (e) {
      console.error('Logout failed', e);
    }
  };

  const decksCount = groups.length;
  const cardsCount = useMemo(() => getTotalCardsCount(groups), [groups]);
  const dueCount = useMemo(() => getTotalDueCardsCount(groups, getDueCards), [groups, getDueCards]);
  const streak = useMemo(() => computeStreak(activityHeatmap), [activityHeatmap]);

  if (isLoading) {
    return <LoadingState />;
  }

  return (
    <AppScreen
      maxWidth={contentMaxWidth}
      brand={<DashboardBrand />}
      right={showPersistentNavigation ? null : (
        <DashboardActions
          user={user}
          onLogin={handleLogin}
          onLogout={handleLogout}
        />
      )}
      overlay={
        <Animated.View
          entering={ZoomIn.springify().delay(getTokenMotionEnterDelay(3))}
          style={styles.fabWrapper}
        >
          <AppFloatingActionButton
            icon="plus"
            style={styles.fab}
            onPress={() => router.push(ROUTES.IMPORT)}
            accessibilityLabel="Create new flashcard deck"
          />
        </Animated.View>
      }
    >
      <SyncStatusBanner
        syncStatus={store.syncStatus}
        lastSyncError={store.lastSyncError}
        lastPersistenceError={store.lastPersistenceError}
        lastStoreError={store.lastStoreError}
      />

      {decksCount > 0 && (
        <AnimatedSection order={1}>
          <DashboardStats
            decksCount={decksCount}
            cardsCount={cardsCount}
            dueCount={dueCount}
            streak={streak}
          />
        </AnimatedSection>
      )}

      {decksCount === 0 ? (
        <AnimatedSection order={2}>
          <EmptyDashboardState />
        </AnimatedSection>
      ) : (
        <DeckGrid groups={groups} studyModes={studyModes} baseOrder={2} />
      )}

      <AppSnackbar
        visible={!!lastLoginError}
        message={t(lastLoginError || '')}
        onDismiss={clearLastLoginError}
      />
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  fabWrapper: {
    position: 'absolute',
    right: TOKENS.spacing.sm,
    bottom: TOKENS.spacing.sm,
    margin: TOKENS.spacing.lg,
  },
  fab: {
    borderRadius: TOKENS.radius.lg,
  },
});

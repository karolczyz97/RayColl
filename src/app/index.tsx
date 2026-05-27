import React, { useMemo } from 'react';
import { StyleSheet, ScrollView, View } from 'react-native';
import Animated, { ZoomIn } from 'react-native-reanimated';
import { FAB } from 'react-native-paper';
import { router } from 'expo-router';
import { useFlashcardStore } from '../hooks/useFlashcardStore';
import { DashboardHeader } from '../components/dashboard/DashboardHeader';
import { DashboardStats } from '../components/dashboard/DashboardStats';
import { EmptyDashboardState } from '../components/dashboard/EmptyDashboardState';
import { DeckGrid } from '../components/dashboard/DeckGrid';
import { computeStreak, getTotalCardsCount, getTotalDueCardsCount } from '../store/selectors/stats';
import { useResponsiveLayout } from '../hooks/useResponsiveLayout';
import { TOKENS } from '../theme/tokens';
import { AppScreen } from '../components/layout/AppScreen';
import { AnimatedSection } from '../components/layout/AnimatedSection';
import { LoadingState } from '../components/layout/LoadingState';
import { SyncStatusBanner } from '../components/feedback/SyncStatusBanner';

export default function Dashboard() {
  const store = useFlashcardStore();
  const { contentMaxWidth } = useResponsiveLayout();

  const { groups, getDueCards, user, signIn, signOut, isLoading } = store;

  const handleLogin = async () => {
    try {
      await signIn();
    } catch (e) {
      console.error('Login failed', e);
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
  const streak = useMemo(() => computeStreak(store.activityHeatmap), [store.activityHeatmap]);

  if (isLoading) {
    return <LoadingState />;
  }

  return (
    <AppScreen scroll={false} maxWidth={contentMaxWidth}>
      <View style={styles.mainContainer}>
        {/* Top Header */}
        <AnimatedSection index={0}>
          <DashboardHeader user={user} onLogin={handleLogin} onLogout={handleLogout} />
        </AnimatedSection>

        <AnimatedSection index={1}>
          <SyncStatusBanner
            syncStatus={store.syncStatus}
            lastSyncError={store.lastSyncError}
            lastPersistenceError={store.lastPersistenceError}
            lastStoreError={store.lastStoreError}
          />
        </AnimatedSection>

        {/* Decks Scroll Container */}
        <ScrollView contentContainerStyle={styles.scrollContent}>
          {/* Quick Statistics Banner */}
          {decksCount > 0 && (
            <AnimatedSection index={2}>
              <DashboardStats
                decksCount={decksCount}
                cardsCount={cardsCount}
                dueCount={dueCount}
                streak={streak}
              />
            </AnimatedSection>
          )}

          {decksCount === 0 ? (
            <AnimatedSection index={3}>
              <EmptyDashboardState />
            </AnimatedSection>
          ) : (
            <AnimatedSection index={3}>
              <DeckGrid
                groups={groups}
                onModeChange={(groupId, modeId) => {
                  const g = groups.find((x) => x.id === groupId);
                  if (g) store.updateGroup({ ...g, activeModeId: modeId });
                }}
              />
            </AnimatedSection>
          )}
        </ScrollView>
      </View>

      {/* Import FAB */}
      <Animated.View entering={ZoomIn.springify().delay(400)} style={styles.fabWrapper}>
        <FAB
          icon="plus"
          style={styles.fab}
          onPress={() => router.push('/import')}
          accessibilityLabel="Create new flashcard deck"
        />
      </Animated.View>
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  mainContainer: {
    flex: 1,
  },
  scrollContent: {
    padding: TOKENS.spacing.lg,
    paddingBottom: 100,
    gap: TOKENS.spacing.lg,
  },
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

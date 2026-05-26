import React, { useMemo } from 'react';
import { StyleSheet, ScrollView, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import Animated, { ZoomIn } from 'react-native-reanimated';
import { FAB, useTheme, ActivityIndicator } from 'react-native-paper';
import { router } from 'expo-router';
import { useFlashcardStore } from '../hooks/useFlashcardStore';
import { DashboardHeader } from '../components/dashboard/DashboardHeader';
import { DashboardStats } from '../components/dashboard/DashboardStats';
import { EmptyDashboardState } from '../components/dashboard/EmptyDashboardState';
import { DeckGrid } from '../components/dashboard/DeckGrid';
import { computeStreak, getTotalCardsCount, getTotalDueCardsCount } from '../store/selectors/stats';
import { useResponsiveLayout } from '../hooks/useResponsiveLayout';
import { TOKENS } from '../theme/tokens';

export default function Dashboard() {
  const store = useFlashcardStore();
  const theme = useTheme();
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
    return (
      <View style={[styles.loadingContainer, { backgroundColor: theme.colors.background }]}>
        <ActivityIndicator size="large" color={theme.colors.primary} />
      </View>
    );
  }

  return (
    <SafeAreaView
      style={[styles.root, { backgroundColor: theme.colors.background }]}
      edges={['top', 'left', 'right']}
    >
      <View style={[styles.mainContainer, { maxWidth: contentMaxWidth }]}>
        {/* Top Header */}
        <DashboardHeader user={user} onLogin={handleLogin} onLogout={handleLogout} />

        {/* Decks Scroll Container */}
        <ScrollView contentContainerStyle={styles.scrollContent}>
          {/* Quick Statistics Banner */}
          {decksCount > 0 && (
            <DashboardStats
              decksCount={decksCount}
              cardsCount={cardsCount}
              dueCount={dueCount}
              streak={streak}
            />
          )}

          {decksCount === 0 ? (
            <EmptyDashboardState />
          ) : (
            <DeckGrid
              groups={groups}
              onModeChange={(groupId, modeId) => {
                const g = groups.find((x) => x.id === groupId);
                if (g) store.updateGroup({ ...g, activeModeId: modeId });
              }}
            />
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
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
  },
  mainContainer: {
    flex: 1,
    width: '100%',
    alignSelf: 'center',
    padding: TOKENS.spacing.xxs,
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
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

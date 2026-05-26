import React, { useMemo } from 'react';
import { View, StyleSheet, ScrollView, useWindowDimensions } from 'react-native';
import { Text, Card, useTheme, ActivityIndicator } from 'react-native-paper';
import { router } from 'expo-router';
import Animated, { FadeInDown } from 'react-native-reanimated';
import { useFlashcardStore } from '../hooks/useFlashcardStore';
import { SegmentedProgressBar, computeCardStats } from '../components/SegmentedProgressBar';
import { PageHeader } from '../components/PageHeader';
import { useI18n } from '../i18n';
import { MaterialCommunityIcons } from '@expo/vector-icons';

function getLocalDateString(d: Date): string {
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function computeStreak(heatmap: Record<string, number>): number {
  const today = new Date();
  let streak = 0;
  for (let i = 0; i < 365; i++) {
    const d = new Date(today);
    d.setDate(d.getDate() - i);
    const key = getLocalDateString(d);
    if (heatmap[key]) streak++;
    else break;
  }
  return streak;
}

function HeatmapGrid({ heatmap, t }: { heatmap: Record<string, number>; t: (key: string) => string }) {
  const theme = useTheme();
  const isDark = theme.dark;

  const cols = 20,
    rows = 7;
  const today = new Date();
  const columnsData: { date: string; count: number }[][] = [];

  // Generate grid columns (from oldest to newest)
  for (let col = cols - 1; col >= 0; col--) {
    const colCells: { date: string; count: number }[] = [];
    for (let row = 0; row < rows; row++) {
      const daysBack = col * 7 + (6 - row);
      const d = new Date(today);
      d.setDate(d.getDate() - daysBack);
      const key = getLocalDateString(d);
      colCells.push({ date: key, count: heatmap[key] || 0 });
    }
    columnsData.push(colCells);
  }

  const colorFor = (count: number): string => {
    if (count === 0) return isDark ? '#2d2d3a' : '#eaeaf0';
    const primary = theme.colors.primary;
    if (primary.startsWith('#')) {
      const base = primary.length === 4
        ? `#${primary[1]}${primary[1]}${primary[2]}${primary[2]}${primary[3]}${primary[3]}`
        : primary;
      if (count <= 2) return `${base}40`; // 25% opacity
      if (count <= 5) return `${base}80`; // 50% opacity
      if (count <= 10) return `${base}c0`; // 75% opacity
      return base; // 100% opacity
    }
    return primary;
  };

  const dayLabels = [
    t('stats.day.mon'),
    '',
    t('stats.day.wed'),
    '',
    t('stats.day.fri'),
    '',
    t('stats.day.sun'),
  ];

  return (
    <View style={styles.heatmapWrapper}>
      {/* Day labels column */}
      <View style={styles.dayLabelsCol}>
        {dayLabels.map((label, i) => (
          <View key={i} style={styles.dayLabelCell}>
            {label ? (
              <Text style={[styles.dayLabelText, { color: theme.colors.outline }]}>
                {label}
              </Text>
            ) : null}
          </View>
        ))}
      </View>

      {/* Grid columns */}
      <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.gridScroll}>
        <View style={styles.gridContainer}>
          {columnsData.map((col, colIdx) => (
            <View key={colIdx} style={styles.gridCol}>
              {col.map((cell, rowIdx) => (
                <View
                  key={rowIdx}
                  style={[
                    styles.gridCell,
                    { backgroundColor: colorFor(cell.count) },
                  ]}
                />
              ))}
            </View>
          ))}
        </View>
      </ScrollView>
    </View>
  );
}

export default function StatsPage() {
  const { t } = useI18n();
  const theme = useTheme();
  const store = useFlashcardStore();
  const { width } = useWindowDimensions();

  const { groups, activityHeatmap, getDueCards, isLoading } = store;

  const totalCards = useMemo(() => groups.reduce((a, g) => a + g.cards.length, 0), [groups]);
  const totalDue = useMemo(() => groups.reduce((a, g) => a + getDueCards(g.id).length, 0), [groups, getDueCards]);
  const streak = useMemo(() => computeStreak(activityHeatmap), [activityHeatmap]);
  const activeDays = useMemo(() => Object.keys(activityHeatmap).length, [activityHeatmap]);

  const allCards = useMemo(() => groups.flatMap((g) => g.cards), [groups]);
  const globalStats = useMemo(() => computeCardStats(allCards), [allCards]);

  const handleBack = () => {
    router.back();
  };

  if (isLoading) {
    return (
      <View style={[styles.centerContainer, { backgroundColor: theme.colors.background }]}>
        <ActivityIndicator size="large" color={theme.colors.primary} />
      </View>
    );
  }

  const statCards = [
    { icon: 'fire', label: t('stats.streak'), value: `${streak} 🔥`, color: '#ffa726' },
    { icon: 'school', label: t('filter.mastered'), value: `${globalStats.mastered}/${totalCards}`, color: '#4caf50' },
    { icon: 'sync', label: t('stats.due_cards'), value: String(totalDue), color: '#f44336' },
    { icon: 'calendar-month', label: t('stats.active_days'), value: String(activeDays), color: '#208AEF' },
  ];

  return (
    <View style={[styles.root, { backgroundColor: theme.colors.background }]}>
      <PageHeader title={t('stats.title')} onBack={handleBack} />

      <ScrollView contentContainerStyle={styles.scrollContent}>
        {/* Quick stats grid */}
        <View style={styles.statsGrid}>
          {statCards.map((m, i) => (
            <Animated.View key={i} entering={FadeInDown.springify().delay(i * 80)} style={{ minWidth: width < 600 ? '47%' : '22%' }}>
            <Card style={styles.statCard} mode="outlined">
              <Card.Content style={styles.statCardContent}>
                <MaterialCommunityIcons name={m.icon as any} size={28} color={m.color} style={{ marginBottom: 4 }} />
                <Text variant="labelSmall" style={{ color: theme.colors.onSurfaceVariant }}>
                  {m.label}
                </Text>
                <Text variant="titleLarge" style={{ fontWeight: 'bold', color: m.color }}>
                  {m.value}
                </Text>
              </Card.Content>
            </Card>
            </Animated.View>
          ))}
        </View>

        {/* Global Progress */}
        <Animated.View entering={FadeInDown.springify().delay(350)}>
        <Card mode="outlined" style={styles.sectionCard}>
          <Card.Content>
            <Text variant="titleMedium" style={styles.sectionCardTitle}>
              {t('stats.progress_title')}
            </Text>
            <SegmentedProgressBar stats={globalStats} showLegend />
          </Card.Content>
        </Card>
        </Animated.View>

        {/* Heatmap */}
        <Card mode="outlined" style={styles.sectionCard}>
          <Card.Content>
            <Text variant="titleMedium" style={styles.sectionCardTitle}>
              {t('stats.heatmap_title')}
            </Text>
            <HeatmapGrid heatmap={activityHeatmap} t={t} />
          </Card.Content>
        </Card>

        {/* Decks Progress */}
        <Card mode="outlined" style={styles.sectionCard}>
          <Card.Content>
            <Text variant="titleMedium" style={styles.sectionCardTitle}>
              {t('stats.deck_progress')}
            </Text>
            {groups.map((g) => {
              const groupStats = computeCardStats(g.cards);
              return (
                <View key={g.id} style={styles.deckRow}>
                  <View style={styles.deckRowHeader}>
                    <Text variant="bodyMedium" style={{ fontWeight: 'bold' }}>
                      {g.name}
                    </Text>
                    <Text variant="bodySmall" style={{ color: theme.colors.onSurfaceVariant }}>
                      {t('stats.cards_count', { count: g.cards.length })}
                    </Text>
                  </View>
                  <SegmentedProgressBar stats={groupStats} />
                </View>
              );
            })}
          </Card.Content>
        </Card>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    paddingHorizontal: 16,
    paddingTop: 48,
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  scrollContent: {
    paddingBottom: 64,
    gap: 16,
  },
  statsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  statCard: {
    flex: 1,
    minWidth: '45%',
    borderRadius: 16,
  },
  statCardContent: {
    alignItems: 'center',
    paddingVertical: 12,
  },
  sectionCard: {
    borderRadius: 16,
  },
  sectionCardTitle: {
    fontWeight: 'bold',
    marginBottom: 16,
  },
  heatmapWrapper: {
    flexDirection: 'row',
  },
  dayLabelsCol: {
    justifyContent: 'space-around',
    paddingRight: 6,
    height: 116,
  },
  dayLabelCell: {
    height: 14,
    justifyContent: 'center',
  },
  dayLabelText: {
    fontSize: 9,
  },
  gridScroll: {
    paddingBottom: 4,
  },
  gridContainer: {
    flexDirection: 'row',
    gap: 3,
    height: 116,
  },
  gridCol: {
    flexDirection: 'column',
    gap: 3,
  },
  gridCell: {
    width: 14,
    height: 14,
    borderRadius: 3,
  },
  deckRow: {
    marginBottom: 16,
  },
  deckRowHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 4,
  },
});

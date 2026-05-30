import React, { useMemo } from 'react';
import { safeBack } from '../utils/navigation';
import { useTheme } from 'react-native-paper';
import { useFlashcardStore } from '../hooks/useFlashcardStore';
import { useI18n } from '../i18n';
import { useResponsiveLayout } from '../hooks/useResponsiveLayout';
import {
  computeStreak,
  getTotalCardsCount,
  getTotalDueCardsCount,
  getActiveDaysCount,
  getGlobalStats,
} from '../store/selectors/stats';
import {
  getDueColor,
  getInfoColor,
  getSuccessColor,
  getWarningColor,
} from '../theme/semanticColors';
import { AppScreen } from '../components/layout/AppScreen';
import { AnimatedSection } from '../components/layout/AnimatedSection';
import { LoadingState } from '../components/layout/LoadingState';
import { SectionCard } from '../components/layout/SectionCard';
import { MetricGrid } from '../components/metrics/MetricGrid';
import { HeatmapGrid } from '../features/stats/HeatmapGrid';
import { DeckProgressList } from '../features/stats/DeckProgressList';

const STATS_METRIC_COMPACT_BREAKPOINT = 720;

export default function StatsPage() {
  const { t } = useI18n();
  const theme = useTheme();
  const store = useFlashcardStore();
  const { formMaxWidth } = useResponsiveLayout();
  const { groups, activityHeatmap, getDueCards, isLoading } = store;

  const totalCards = useMemo(() => getTotalCardsCount(groups), [groups]);
  const totalDue = useMemo(() => getTotalDueCardsCount(groups, getDueCards), [getDueCards, groups]);
  const streak = useMemo(() => computeStreak(activityHeatmap), [activityHeatmap]);
  const activeDays = useMemo(() => getActiveDaysCount(activityHeatmap), [activityHeatmap]);
  const globalStats = useMemo(() => getGlobalStats(groups), [groups]);

  if (isLoading) {
    return <LoadingState />;
  }

  const statCards = [
    {
      icon: 'fire',
      label: t('stats.streak'),
      value: `${streak} \u{1F525}`,
      color: getWarningColor(theme),
    },
    {
      icon: 'school',
      label: t('filter.mastered'),
      value: `${globalStats.mastered}/${totalCards}`,
      color: getSuccessColor(theme),
    },
    {
      icon: 'sync',
      label: t('stats.due_cards'),
      value: String(totalDue),
      color: getDueColor(theme),
    },
    {
      icon: 'calendar-month',
      label: t('stats.active_days'),
      value: String(activeDays),
      color: getInfoColor(theme),
    },
  ];

  return (
    <AppScreen title={t('stats.title')} onBack={safeBack} maxWidth={formMaxWidth}>
      <AnimatedSection index={0}>
        <MetricGrid
          items={statCards}
          compactBreakpoint={STATS_METRIC_COMPACT_BREAKPOINT}
          screenMaxWidth={formMaxWidth}
          hasScrollViewPadding={false}
        />
      </AnimatedSection>

      <AnimatedSection index={1}>
        <SectionCard title={t('stats.deck_progress')} titleAlign="center">
          <DeckProgressList
            groups={groups}
            overallStats={globalStats}
            totalCards={totalCards}
            t={t}
          />
        </SectionCard>
      </AnimatedSection>

      <AnimatedSection index={2} style={styles.raisedSection}>
        <SectionCard title={t('stats.heatmap_title')} titleAlign="center">
          <HeatmapGrid heatmap={activityHeatmap} t={t} />
        </SectionCard>
      </AnimatedSection>
    </AppScreen>
  );
}

const styles = {
  raisedSection: {
    marginTop: -8,
  },
};

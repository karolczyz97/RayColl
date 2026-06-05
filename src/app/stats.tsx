import React, { useMemo } from 'react';

import { safeBack } from '@/utils/navigation';
import { useTheme } from 'react-native-paper';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { useI18n } from '@/i18n';
import { useResponsiveLayout } from '@/hooks/useResponsiveLayout';
import {
  computeStreak,
  getTotalCardsCount,
  getTotalDueCardsCount,
  getActiveDaysCount,
  getGlobalStats,
} from '@/store/selectors/stats';
import {
  getDueColor,
  getInfoColor,
  getSuccessColor,
  getWarningColor,
} from '@/theme/semanticColors';
import { AppScreen } from '@/components/layout/AppScreen';
import { AnimatedSection } from '@/components/layout/AnimatedSection';
import { LoadingState } from '@/components/layout/LoadingState';
import { SectionCard } from '@/components/layout/SectionCard';
import { MetricGrid } from '@/components/metrics/MetricGrid';
import { HeatmapGrid } from '@/features/stats/HeatmapGrid';
import { DeckProgressList } from '@/features/stats/DeckProgressList';


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
      value: String(streak),
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
    <AppScreen
      title={t('stats.title')}
      onBack={safeBack}
      maxWidth={formMaxWidth}
    >
      <AnimatedSection order={0}>
        <MetricGrid
          items={statCards}
          screenMaxWidth={formMaxWidth}
        />
      </AnimatedSection>

      <AnimatedSection order={1}>
        <SectionCard title={t('stats.deck_progress')} titleAlign="center">
          <DeckProgressList
            groups={groups}
            overallStats={globalStats}
            totalCards={totalCards}
          />
        </SectionCard>
      </AnimatedSection>

      <AnimatedSection order={2}>
        <SectionCard title={t('stats.heatmap_title')} titleAlign="center">
          <HeatmapGrid heatmap={activityHeatmap} />
        </SectionCard>
      </AnimatedSection>
    </AppScreen>
  );
}

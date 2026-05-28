import React from 'react';
import { useTheme } from 'react-native-paper';
import { useI18n } from '../../i18n';
import { getWarningColor } from '../../theme/semanticColors';
import { METRIC_GRID_COMPACT_BREAKPOINT, MetricGrid } from '../metrics/MetricGrid';

interface Props {
  decksCount: number;
  cardsCount: number;
  dueCount: number;
  streak: number;
}

export function DashboardStats({ decksCount, cardsCount, dueCount, streak }: Props) {
  const { t } = useI18n();
  const theme = useTheme();

  const stats = [
    {
      icon: 'book-multiple',
      label: t('stats.decks_title') || 'Talie',
      value: String(decksCount),
      color: theme.colors.primary,
    },
    {
      icon: 'card-multiple-outline',
      label: t('stats.cards_title') || 'Fiszki',
      value: String(cardsCount),
      color: theme.colors.secondary,
    },
    {
      icon: 'sync',
      label: t('stats.due_cards'),
      value: String(dueCount),
      color: dueCount > 0 ? theme.colors.error : theme.colors.outline,
    },
    {
      icon: 'fire',
      label: t('stats.streak'),
      value: `${streak} \u{1F525}`,
      color: getWarningColor(theme),
    },
  ];

  return (
    <MetricGrid items={stats} compactBreakpoint={METRIC_GRID_COMPACT_BREAKPOINT} />
  );
}

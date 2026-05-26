import React from 'react';
import { View, StyleSheet, Text } from 'react-native';
import { useTheme } from 'react-native-paper';
import type { Flashcard } from '../types/models';
import { getCardCategory } from '../srs/srsEngine';
import { useI18n } from '../i18n';
import { getReviewStatusColor } from '../theme/semanticColors';

export interface CardStats {
  total: number;
  newCount: number;
  learning: number; // state 1 or 3
  review: number; // state 2, not yet mastered
  mastered: number; // state 2, repetitions >= 3
}

export function computeCardStats(cards: Flashcard[]): CardStats {
  let newCount = 0,
    learning = 0,
    review = 0,
    mastered = 0;
  for (const c of cards) {
    const category = getCardCategory(c.srsState);
    if (category === 'new') newCount++;
    else if (category === 'learning') learning++;
    else if (category === 'review') review++;
    else if (category === 'mastered') mastered++;
  }
  return { total: cards.length, newCount, learning, review, mastered };
}

interface Props {
  stats: CardStats;
  height?: number;
  showLegend?: boolean;
}

export function SegmentedProgressBar({ stats, height = 12, showLegend = false }: Props) {
  const { total, newCount, learning, review, mastered } = stats;
  const { t } = useI18n();
  const theme = useTheme();

  const masteredColors = getReviewStatusColor(theme, 'mastered');
  const reviewColors = getReviewStatusColor(theme, 'review');
  const learningColors = getReviewStatusColor(theme, 'learning');
  const newColors = getReviewStatusColor(theme, 'new');

  // MD3 Expressive tonal color tokens
  const colors = {
    mastered: masteredColors.color,
    review: reviewColors.color,
    learning: learningColors.color,
    new: newColors.color,
    empty: theme.colors.outlineVariant || '#e0e0e0',
  };

  if (total === 0) {
    return (
      <View
        style={[styles.bar, { height, borderRadius: height / 2, backgroundColor: colors.empty }]}
      />
    );
  }

  const segments = [
    { count: mastered, color: colors.mastered, label: t('srs.badge.mastered') },
    { count: review, color: colors.review, label: t('srs.badge.review') },
    { count: learning, color: colors.learning, label: t('srs.badge.learning') },
    { count: newCount, color: colors.new, label: t('srs.badge.new') },
  ].filter((s) => s.count > 0);

  return (
    <View style={styles.container}>
      <View
        style={[styles.bar, { height, borderRadius: height / 2, backgroundColor: colors.empty }]}
      >
        {segments.map((seg, i) => {
          const widthPercent = (seg.count / total) * 100;
          return (
            <View
              key={i}
              style={{
                width: `${widthPercent}%`,
                height: '100%',
                backgroundColor: seg.color,
              }}
            />
          );
        })}
      </View>
      {showLegend && (
        <View style={styles.legendContainer}>
          {[
            {
              color: colors.mastered,
              bg: masteredColors.bg,
              label: t('srs.badge.mastered'),
              count: mastered,
            },
            {
              color: colors.review,
              bg: reviewColors.bg,
              label: t('srs.badge.review'),
              count: review,
            },
            {
              color: colors.learning,
              bg: learningColors.bg,
              label: t('srs.badge.learning'),
              count: learning,
            },
            {
              color: colors.new,
              bg: newColors.bg,
              label: t('srs.badge.new'),
              count: newCount,
            },
          ].map((item, i) => (
            <View key={i} style={[styles.legendItem, { backgroundColor: item.bg }]}>
              <View style={[styles.dot, { backgroundColor: item.color }]} />
              <Text style={[styles.legendText, { color: item.color }]}>
                {item.label} ({item.count})
              </Text>
            </View>
          ))}
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    width: '100%',
  },
  bar: {
    flexDirection: 'row',
    overflow: 'hidden',
    width: '100%',
  },
  legendContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    gap: 12,
    marginTop: 10,
  },
  legendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    paddingHorizontal: 8,
    paddingVertical: 3,
    borderRadius: 12,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  legendText: {
    fontSize: 11,
    fontWeight: '600',
  },
});

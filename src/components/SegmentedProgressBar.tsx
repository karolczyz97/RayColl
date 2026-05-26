import React from 'react';
import { View, StyleSheet, Text } from 'react-native';
import { useTheme } from 'react-native-paper';
import type { CardStats } from '../store/selectors/stats';
import { useI18n } from '../i18n';
import { getReviewStatusColor } from '../theme/semanticColors';
import { SRS_CATEGORY_ORDER, SRS_CATEGORIES_TOKENS } from '../theme/srsTokens';

const STATS_KEY: Record<string, keyof CardStats> = {
  new: 'newCount',
  learning: 'learning',
  review: 'review',
  mastered: 'mastered',
};

interface Props {
  stats: CardStats;
  height?: number;
  showLegend?: boolean;
}

export function SegmentedProgressBar({ stats, height = 12, showLegend = false }: Props) {
  const { total } = stats;
  const { t } = useI18n();
  const theme = useTheme();
  const emptyColor = theme.colors.outlineVariant;

  const categoryData = SRS_CATEGORY_ORDER.map((category) => {
    const statsKey = STATS_KEY[category];
    const count = (stats[statsKey] as number) ?? 0;
    const { color, bg } = getReviewStatusColor(theme, category);
    const label = t(SRS_CATEGORIES_TOKENS[category].labelKey);
    return { category, count, color, bg, label };
  });

  if (total === 0) {
    return (
      <View
        style={[styles.bar, { height, borderRadius: height / 2, backgroundColor: emptyColor }]}
      />
    );
  }

  const segments = categoryData.filter((data) => data.count > 0);

  return (
    <View style={styles.container}>
      <View
        style={[styles.bar, { height, borderRadius: height / 2, backgroundColor: emptyColor }]}
      >
        {segments.map((segment) => {
          const widthPercent = (segment.count / total) * 100;
          return (
            <View
              key={segment.category}
              style={{
                width: `${widthPercent}%`,
                height: '100%',
                backgroundColor: segment.color,
              }}
            />
          );
        })}
      </View>
      {showLegend && (
        <View style={styles.legendContainer}>
          {categoryData.map((item) => (
            <View key={item.category} style={[styles.legendItem, { backgroundColor: item.bg }]}>
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

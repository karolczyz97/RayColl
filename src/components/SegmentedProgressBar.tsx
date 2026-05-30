import React from 'react';
import { View, StyleSheet, Text } from 'react-native';
import type { MD3Theme } from 'react-native-paper';
import { useTheme } from 'react-native-paper';
import type { CardStats } from '../store/selectors/stats';
import type { SrsCardCategory } from '../srs/srsEngine';
import type { TranslationFn } from '../i18n';
import { useI18n } from '../i18n';
import { getReviewStatusColor } from '../theme/semanticColors';
import { SRS_CATEGORY_ORDER, SRS_CATEGORIES_TOKENS } from '../theme/srsTokens';
import { TOKENS } from '../theme/tokens';

const STATS_KEY: Record<string, keyof CardStats> = {
  new: 'newCount',
  learning: 'learning',
  review: 'review',
  mastered: 'mastered',
};

export interface SessionProgressItem {
  id: string;
  category: SrsCardCategory;
}

interface StatsModeProps {
  mode?: 'stats';
  stats: CardStats;
  height?: number;
  showLegend?: boolean;
  legendPosition?: 'top' | 'bottom';
}

interface SessionModeProps {
  mode: 'session';
  items: SessionProgressItem[];
  currentIndex: number;
  height?: number;
}

type Props = StatsModeProps | SessionModeProps;

export function SegmentedProgressBar(props: Props) {
  const theme = useTheme();
  const { t } = useI18n();
  const emptyColor = theme.colors.outlineVariant;

  if ('mode' in props && props.mode === 'session') {
    return renderSessionMode(props, theme, emptyColor);
  }

  return renderStatsMode(props, theme, emptyColor, t);
}

function renderStatsMode(
  { stats, height = 12, showLegend = false, legendPosition = 'bottom' }: StatsModeProps,
  theme: MD3Theme,
  emptyColor: string,
  t: TranslationFn,
) {
  const { total } = stats;

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
      {showLegend && legendPosition === 'top' ? (
        <View style={[styles.legendContainer, styles.legendTop]}>
          {categoryData.map((item) => (
            <View key={item.category} style={[styles.legendItem, { backgroundColor: item.bg }]}>
              <View style={[styles.dot, { backgroundColor: item.color }]} />
              <Text style={[styles.legendText, { color: item.color }]}>
                {item.label} ({item.count})
              </Text>
            </View>
          ))}
        </View>
      ) : null}
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
      {showLegend && legendPosition === 'bottom' && (
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

function renderSessionMode(
  { items, currentIndex, height = 8 }: SessionModeProps,
  theme: MD3Theme,
  emptyColor: string,
) {
  if (items.length === 0) {
    return (
      <View
        style={[styles.bar, { height, borderRadius: height / 2, backgroundColor: emptyColor }]}
      />
    );
  }

  return (
    <View style={styles.container}>
      <View
        style={[
          styles.bar,
          { height, borderRadius: height / 2, backgroundColor: emptyColor },
          styles.sessionBar,
        ]}
      >
        {items.map((item, index) => {
          let bgColor = emptyColor;
          let isActive = false;

          if (index < currentIndex) {
            const { color } = getReviewStatusColor(theme, item.category);
            bgColor = color;
          } else if (index === currentIndex) {
            const { color } = getReviewStatusColor(theme, item.category);
            bgColor = color;
            isActive = true;
          }

          return (
            <View
              key={item.id}
              style={[
                styles.sessionSegment,
                { backgroundColor: bgColor },
                isActive && { borderWidth: 1, borderColor: theme.colors.onSurface },
              ]}
            />
          );
        })}
      </View>
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
    gap: TOKENS.spacing.md,
    marginTop: 10,
  },
  legendTop: {
    marginTop: 0,
    marginBottom: 10,
  },
  legendItem: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: TOKENS.spacing.xs,
    paddingHorizontal: TOKENS.spacing.sm,
    paddingVertical: 3,
    borderRadius: TOKENS.radius.md,
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
  sessionBar: {
    gap: 0.5,
  },
  sessionSegment: {
    flex: 1,
    height: '100%',
  },

});

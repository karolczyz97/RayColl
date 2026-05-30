import React from 'react';
import { View, StyleSheet, Text } from 'react-native';
import type { MD3Theme } from 'react-native-paper';
import { TouchableRipple, useTheme } from 'react-native-paper';
import type { CardStats } from '../store/selectors/stats';
import type { TranslationFn } from '../i18n';
import { useI18n } from '../i18n';
import type { SrsCardCategory } from '../srs/srsEngine';
import { getReviewStatusColor } from '../theme/semanticColors';
import { SRS_CATEGORY_ORDER, SRS_CATEGORIES_TOKENS } from '../theme/srsTokens';
import { TOKENS } from '../theme/tokens';
import {
  getSessionProgressSegments,
  type SessionProgressItem,
} from '../features/study/session/sessionProgress';

const STATS_KEY: Record<string, keyof CardStats> = {
  new: 'newCount',
  learning: 'learning',
  review: 'review',
  mastered: 'mastered',
};

interface StatsModeProps {
  mode?: 'stats';
  stats: CardStats;
  height?: number;
  showLegend?: boolean;
  legendPosition?: 'top' | 'bottom';
  selectedCategories?: SrsCardCategory[];
  onCategoryToggle?: (category: SrsCardCategory) => void;
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
  {
    stats,
    height = 12,
    showLegend = false,
    legendPosition = 'bottom',
    selectedCategories,
    onCategoryToggle,
  }: StatsModeProps,
  theme: MD3Theme,
  emptyColor: string,
  t: TranslationFn,
) {
  const { total } = stats;
  const isFiltering = selectedCategories != null && selectedCategories.length > 0;

  const categoryData = SRS_CATEGORY_ORDER.map((category) => {
    const statsKey = STATS_KEY[category];
    const count = (stats[statsKey] as number) ?? 0;
    const { color, bg } = getReviewStatusColor(theme, category);
    const label = t(SRS_CATEGORIES_TOKENS[category].labelKey);
    const isActive = !isFiltering || selectedCategories?.includes(category);
    return { category, count, color, bg, label, isActive };
  });

  if (total === 0) {
    return (
      <View
        style={[styles.bar, { height, borderRadius: height / 2, backgroundColor: emptyColor }]}
      />
    );
  }

  const segments = categoryData.filter((data) => data.count > 0);

  const renderLegendItem = (item: (typeof categoryData)[0]) => {
    const isEmpty = item.count === 0;
    const isActive = item.isActive && !isEmpty;

    const dotColor = isActive ? item.bg : theme.colors.outlineVariant;
    const textColor = isActive ? theme.colors.onSurface : theme.colors.onSurfaceVariant;
    const pillOpacity = isEmpty ? 0.35 : isActive ? 1 : 0.5;

    const pillContent = (
      <View
        style={[
          styles.pill,
          {
            backgroundColor: theme.colors.surface,
            opacity: pillOpacity,
            borderColor: isActive ? item.bg : theme.colors.outlineVariant,
          },
        ]}
      >
        <View style={[styles.dot, { backgroundColor: dotColor }]} />
        <Text style={[styles.pillText, { color: textColor }]}>
          {item.label} ({item.count})
        </Text>
      </View>
    );

    if (onCategoryToggle && !isEmpty) {
      return (
        <TouchableRipple
          key={item.category}
          onPress={() => onCategoryToggle(item.category)}
          style={styles.pillRipple}
          borderless
          accessibilityRole="checkbox"
          accessibilityState={{ checked: isActive }}
          accessibilityLabel={`${item.label} (${item.count})`}
        >
          {pillContent}
        </TouchableRipple>
      );
    }

    return <View key={item.category}>{pillContent}</View>;
  };

  return (
    <View style={styles.container}>
      {showLegend && legendPosition === 'top' ? (
        <View style={[styles.legendContainer, styles.legendTop]}>
          {categoryData.map(renderLegendItem)}
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
                backgroundColor: segment.isActive ? segment.color : emptyColor,
              }}
            />
          );
        })}
      </View>
      {showLegend && legendPosition === 'bottom' && (
        <View style={styles.legendContainer}>
          {categoryData.map(renderLegendItem)}
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

  const segments = getSessionProgressSegments(items, currentIndex);

  return (
    <View style={styles.container}>
      <View
        style={[
          styles.bar,
          styles.sessionBar,
          { height, borderRadius: height / 2, backgroundColor: emptyColor },
        ]}
      >
        {segments.map((segment) => {
          const { color } = getReviewStatusColor(theme, segment.category);
          const backgroundColor = segment.state === 'future' ? emptyColor : color;
          const isCurrent = segment.state === 'current';

          return (
            <View
              key={segment.id}
              style={[
                styles.sessionSegment,
                { backgroundColor },
                isCurrent && {
                  borderWidth: 1,
                  borderColor: theme.colors.onSurface,
                },
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
    gap: TOKENS.spacing.sm,
    marginTop: 10,
  },
  legendTop: {
    marginTop: 0,
    marginBottom: 10,
  },
  pill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: TOKENS.spacing.xs,
    paddingHorizontal: TOKENS.spacing.md,
    paddingVertical: TOKENS.spacing.sm,
    borderRadius: TOKENS.radius.pill,
    borderWidth: 1,
  },
  pillRipple: {
    borderRadius: TOKENS.radius.pill,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
  pillText: {
    fontSize: 11,
    fontWeight: '600',
  },
  sessionBar: {
    gap: 1,
  },
  sessionSegment: {
    flex: 1,
    height: '100%',
  },
});

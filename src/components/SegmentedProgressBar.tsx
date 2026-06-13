import React from 'react';
import { View, StyleSheet } from 'react-native';
import type { MD3Theme } from 'react-native-paper';
import { Text, TouchableRipple, useTheme } from 'react-native-paper';
import type { CardStats } from '@/store/selectors/stats';
import type { TranslationFn } from '@/i18n';
import { useI18n } from '@/i18n';
import type { SrsCardCategory } from '@/srs/srsEngine';
import { getReviewStatusColor } from '@/theme/semanticColors';
import {
  SRS_CATEGORY_ORDER,
  SRS_CATEGORIES_TOKENS,
  CATEGORY_TO_STATS_KEY,
} from '@/theme/srsTokens';
import { formatSrsCountLabel } from '@/i18n/plural';
import { TOKENS } from '@/theme/tokens';
import {
  getSessionProgressSegments,
  type SessionProgressItem,
} from '@/features/study/session/sessionProgress';
import { ExpressiveProgress, ExpressiveSegmentedProgress } from './expressive';

const INTERACTIVE_PROGRESS_HEIGHT = TOKENS.touchTarget.compact;

interface StatsModeProps {
  mode?: 'stats';
  stats: CardStats;
  height?: number;
  showLegend?: boolean;
  legendPosition?: 'top' | 'bottom';
  selectedCategories?: SrsCardCategory[];
  onCategoryToggle?: (category: SrsCardCategory) => void;
  /**
   * Render category labels/icons inside the bar (the tall "browse" look) even
   * when the bar is not interactive. Used by the stats overall-progress bar so
   * it matches the deck-preview bar.
   */
  showInlineLabels?: boolean;
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
  const { t, language } = useI18n();
  const emptyColor = theme.colors.outlineVariant;

  if ('mode' in props && props.mode === 'session') {
    return renderSessionMode(props, theme, emptyColor);
  }

  return renderStatsMode(props, theme, emptyColor, t, language);
}

function renderStatsMode(
  {
    stats,
    height = TOKENS.control.progressHeight,
    showLegend = false,
    legendPosition = 'bottom',
    selectedCategories,
    onCategoryToggle,
    showInlineLabels = false,
  }: StatsModeProps,
  theme: MD3Theme,
  emptyColor: string,
  t: TranslationFn,
  language: string,
) {
  const { total } = stats;
  const isFiltering = selectedCategories != null && selectedCategories.length > 0;
  const isInteractive = !!onCategoryToggle;
  // Both interactive (browse filter) and inline-label (stats overall) bars use
  // the tall layout with content rendered inside each segment.
  const hasInlineContent = isInteractive || showInlineLabels;
  const progressHeight = hasInlineContent ? INTERACTIVE_PROGRESS_HEIGHT : height;

  const categoryData = SRS_CATEGORY_ORDER.map((category) => {
    const statsKey = CATEGORY_TO_STATS_KEY[category];
    const count = (stats[statsKey] as number) ?? 0;
    const { fg, bg } = getReviewStatusColor(theme, category);
    const label = t(SRS_CATEGORIES_TOKENS[category].labelKey);
    const iconName = SRS_CATEGORIES_TOKENS[category].iconName;
    const isActive = !isFiltering || selectedCategories?.includes(category);
    return { category, count, color: fg, bg, label, iconName, isActive };
  });

  if (total === 0) {
    return (
      <ExpressiveProgress
        value={0}
        max={1}
        height={progressHeight}
        colorRole="surface"
      />
    );
  }

  const segments = categoryData.filter((data) => data.count > 0);
  const a11yLabel = categoryData
    .map((d) => `${d.label}: ${d.count}`)
    .join(', ');

  const renderLegendItem = (item: (typeof categoryData)[0]) => {
    const isEmpty = item.count === 0;
    const isActive = item.isActive && !isEmpty;

    const accentColor = item.bg;
    const dotColor = isActive ? accentColor : theme.colors.outlineVariant;
    const textColor = isActive ? theme.colors.onSurface : theme.colors.onSurfaceVariant;
    const pillOpacity = isEmpty ? 0.35 : isActive ? 1 : 0.5;

    const pillContent = (
      <View
        style={[
          styles.pill,
          {
            backgroundColor: theme.colors.surface,
            opacity: pillOpacity,
            borderColor: isActive ? accentColor : theme.colors.outlineVariant,
          },
        ]}
      >
        <View style={[styles.dot, { backgroundColor: dotColor }]} />
        <Text variant="labelSmall" style={[styles.pillText, { color: textColor }]}>
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
      <ExpressiveSegmentedProgress
        height={progressHeight}
        accessibilityLabel={isInteractive ? undefined : a11yLabel}
        segments={segments.map((segment) => {
          const isSegmentInteractive = !!onCategoryToggle;
          // Every inline segment shows "<count> <icon>", with the count and icon
          // in the category's accent color on its tinted fill. The segment grows
          // to fit this content (see ExpressiveSegmentedProgress).
          const label = hasInlineContent ? `${segment.count}` : undefined;
          const icon = hasInlineContent ? segment.iconName : undefined;

          return {
            id: segment.category,
            value: segment.count,
            color: segment.isActive ? segment.bg : emptyColor,
            label,
            icon,
            labelColor: segment.isActive ? segment.color : theme.colors.onSurfaceVariant,
            accessibilityLabel: formatSrsCountLabel(segment.category, segment.count, t, language, CATEGORY_TO_STATS_KEY[segment.category]),
            accessibilityRole: isSegmentInteractive ? ('checkbox' as const) : undefined,
            accessibilityState: isSegmentInteractive
              ? { checked: segment.isActive }
              : undefined,
            onPress: onCategoryToggle ? () => onCategoryToggle(segment.category) : undefined,
          };
        })}
      />
      {showLegend && legendPosition === 'bottom' && (
        <View style={styles.legendContainer}>
          {categoryData.map(renderLegendItem)}
        </View>
      )}
    </View>
  );
}

function renderSessionMode(
  { items, currentIndex, height = TOKENS.control.progressHeight }: SessionModeProps,
  theme: MD3Theme,
  emptyColor: string,
) {
  if (items.length === 0) {
    return (
      <ExpressiveProgress
        value={0}
        max={1}
        height={height}
        colorRole="surface"
      />
    );
  }

  const segments = getSessionProgressSegments(items, currentIndex);

  return (
    <View style={styles.container}>
      <ExpressiveSegmentedProgress
        height={height}
        segments={segments.map((segment) => {
          const { bg } = getReviewStatusColor(theme, segment.category);
          const segmentColor = segment.state === 'future' ? emptyColor : bg;

          return {
            id: segment.id,
            value: 1,
            color: segmentColor,
          };
        })}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    width: '100%',
  },
  legendContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'center',
    gap: TOKENS.spacing.sm,
    marginTop: TOKENS.spacing.sm,
  },
  legendTop: {
    marginTop: 0,
    marginBottom: TOKENS.spacing.sm,
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
    width: TOKENS.spacing.sm,
    height: TOKENS.spacing.sm,
    borderRadius: TOKENS.radius.pill,
  },
  pillText: {
    fontWeight: TOKENS.typography.weight.semibold,
  },
});

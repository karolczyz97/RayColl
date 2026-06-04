import React from 'react';
import { Platform, StyleSheet, View } from 'react-native';
import { useTheme } from 'react-native-paper';

import { TOKENS } from '@/theme/tokens';

export interface ExpressiveProgressSegment {
  id: string;
  value: number;
  color: string;
}

interface ExpressiveSegmentedProgressProps {
  segments: readonly ExpressiveProgressSegment[];
  height?: number;
  accessibilityLabel?: string;
}

/**
 * Segmented linear progress rendered with plain Views: a rounded track that
 * clips a flex row of butted color segments (each `flexGrow` = its value). The
 * uniform `borderRadius` keeps both ends crisply rounded at any width, unlike
 * the previous SVG version which distorted the corner radius via
 * `preserveAspectRatio="none"`.
 */
export function ExpressiveSegmentedProgress({
  segments,
  height = TOKENS.control.progressHeight,
  accessibilityLabel,
}: ExpressiveSegmentedProgressProps) {
  const theme = useTheme();
  const total = segments.reduce((sum, segment) => sum + Math.max(0, segment.value), 0);
  const safeHeight = Math.max(1, height);
  const radius = safeHeight / 2;
  const wrapperAccessibilityProps = Platform.OS === 'web'
    ? accessibilityLabel
      ? {
          role: 'progressbar' as const,
          'aria-label': accessibilityLabel,
        }
      : { 'aria-hidden': true }
    : {
        accessible: accessibilityLabel ? true : undefined,
        accessibilityRole: accessibilityLabel ? ('progressbar' as const) : undefined,
        accessibilityLabel,
        accessibilityElementsHidden: !accessibilityLabel,
        importantForAccessibility: accessibilityLabel
          ? ('auto' as const)
          : ('no-hide-descendants' as const),
      };

  return (
    <View
      testID="expressive-segmented-progress-track"
      style={[
        styles.track,
        { height: safeHeight, borderRadius: radius, backgroundColor: theme.colors.surfaceVariant },
      ]}
      {...wrapperAccessibilityProps}
    >
      {total > 0 ? (
        <View testID="expressive-segmented-progress-row" style={styles.row}>
          {segments.map((segment) => {
            const weight = Math.max(0, segment.value);
            if (weight <= 0) {
              return null;
            }
            return (
              <View
                key={segment.id}
                testID={`expressive-progress-segment-${segment.id}`}
                style={{ flexGrow: weight, flexBasis: 0, backgroundColor: segment.color }}
              />
            );
          })}
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  track: {
    width: '100%',
    overflow: 'hidden',
  },
  row: {
    flexDirection: 'row',
    width: '100%',
    height: '100%',
  },
});

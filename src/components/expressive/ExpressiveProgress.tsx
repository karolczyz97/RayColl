import React from 'react';
import { Platform, StyleSheet, View } from 'react-native';
import { useTheme } from 'react-native-paper';

import { TOKENS } from '@/theme/tokens';
import { getProgressAccessibilityValue, getProgressRatio } from './expressiveGeometry';
import { getExpressiveColors, type ExpressiveColorRole } from './expressiveColors';

export interface ExpressiveProgressProps {
  value: number;
  max: number;
  height?: number;
  colorRole?: ExpressiveColorRole;
  accessibilityLabel?: string;
}

/**
 * Contained linear progress rendered with plain Views + uniform `borderRadius`,
 * so the rounded "pill" ends stay crisp at any width. (An earlier SVG version
 * stretched the corner radius into a distorted ellipse via
 * `preserveAspectRatio="none"`.)
 */
export function ExpressiveProgress({
  value,
  max,
  height = TOKENS.control.progressHeight,
  colorRole = 'primary',
  accessibilityLabel,
}: ExpressiveProgressProps) {
  const theme = useTheme();
  const colors = getExpressiveColors(theme, colorRole);
  const ratio = getProgressRatio({ value, max });
  const safeHeight = Math.max(1, height);
  const radius = safeHeight / 2;
  const accessibilityValue = getProgressAccessibilityValue({ value, max });
  const wrapperAccessibilityProps = Platform.OS === 'web'
    ? accessibilityLabel
      ? {
          role: 'progressbar' as const,
          'aria-label': accessibilityLabel,
          'aria-valuemin': accessibilityValue.min,
          'aria-valuemax': accessibilityValue.max,
          'aria-valuenow': accessibilityValue.now,
        }
      : { 'aria-hidden': true }
    : {
        accessible: accessibilityLabel ? true : undefined,
        accessibilityRole: accessibilityLabel ? ('progressbar' as const) : undefined,
        accessibilityLabel,
        accessibilityValue: accessibilityLabel ? accessibilityValue : undefined,
        accessibilityElementsHidden: !accessibilityLabel,
        importantForAccessibility: accessibilityLabel
          ? ('auto' as const)
          : ('no-hide-descendants' as const),
      };

  return (
    <View
      testID="expressive-progress-track"
      style={[
        styles.track,
        { height: safeHeight, borderRadius: radius, backgroundColor: colors.container },
      ]}
      {...wrapperAccessibilityProps}
    >
      {ratio > 0 ? (
        <View
          testID="expressive-progress-fill"
          style={[
            styles.fill,
            { width: `${ratio * 100}%`, backgroundColor: colors.fill },
          ]}
        />
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  track: {
    width: '100%',
    overflow: 'hidden',
  },
  fill: {
    height: '100%',
  },
});

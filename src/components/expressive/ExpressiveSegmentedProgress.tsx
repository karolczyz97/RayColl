import React, { useCallback, useState } from 'react';
import {
  Platform,
  Pressable,
  StyleSheet,
  Text,
  View,
  type AccessibilityRole,
  type AccessibilityState,
  type DimensionValue,
} from 'react-native';
import { useTheme } from 'react-native-paper';

import { AppIcon } from '@/components/AppIcon';
import { TOKENS } from '@/theme/tokens';

const INTERACTIVE_SEGMENT_MIN_WIDTH = '12.5%' as DimensionValue;
// Extra px added to the measured label width so sub-pixel rounding never trims
// the last glyph into an ellipsis.
const LABEL_FIT_BUFFER = 2;
// Extra breathing room so a labeled segment is a touch wider than its text,
// not exactly text-tight.
const LABEL_WIDTH_SLACK = TOKENS.spacing.md;
const SEGMENT_ICON_SIZE = TOKENS.iconSize.xs;
// Gap between the count text and the icon when both are shown ("5 🏆").
const CONTENT_GAP = TOKENS.spacing.xs;

export interface ExpressiveProgressSegment {
  id: string;
  value: number;
  color: string;
  accessibilityLabel?: string;
  accessibilityRole?: AccessibilityRole;
  accessibilityState?: AccessibilityState;
  /** Text shown inside the segment (full label, or just the count beside an icon). */
  label?: string;
  /** MaterialCommunityIcons name shown after `label` (e.g. count + icon on narrow screens). */
  icon?: string;
  labelColor?: string;
  onPress?: () => void;
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
  const [labelWidths, setLabelWidths] = useState<Record<string, number>>({});

  const handleLabelMeasure = useCallback((id: string, width: number) => {
    setLabelWidths(prev => (prev[id] === width ? prev : { ...prev, [id]: width }));
  }, []);

  const total = segments.reduce((sum, segment) => sum + Math.max(0, segment.value), 0);
  const hasInteractiveSegments = segments.some((segment) => segment.onPress);
  const safeHeight = Math.max(1, height);
  const radius = safeHeight / 2;
  const wrapperAccessibilityProps = hasInteractiveSegments
    ? {}
    : Platform.OS === 'web'
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

  const labeledSegments = segments.filter(s => s.label);

  return (
    <View style={styles.wrapper}>
      {labeledSegments.length > 0 && (
        <View
          style={styles.measureLayer}
          pointerEvents="none"
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants"
        >
          {labeledSegments.map(s => (
            <Text
              key={s.id}
              testID={`expressive-progress-measure-${s.id}`}
              style={[theme.fonts.labelLarge, styles.measureLabel]}
              onLayout={e => handleLabelMeasure(s.id, Math.ceil(e.nativeEvent.layout.width))}
            >
              {s.label}
            </Text>
          ))}
        </View>
      )}
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
            const hasLabel = !!segment.label;
            const hasIcon = !!segment.icon;
            const rawTextWidth = hasLabel ? labelWidths[segment.id] : undefined;
            let segmentMinWidth: DimensionValue = 0;
            if (hasLabel || hasIcon) {
              if (hasLabel && rawTextWidth == null) {
                // Text not measured yet: hold a sensible floor for one frame.
                segmentMinWidth = INTERACTIVE_SEGMENT_MIN_WIDTH;
              } else {
                const textPart = rawTextWidth ?? 0;
                const iconPart = hasIcon ? SEGMENT_ICON_SIZE : 0;
                const gapPart = hasLabel && hasIcon ? CONTENT_GAP : 0;
                segmentMinWidth =
                  textPart +
                  iconPart +
                  gapPart +
                  TOKENS.spacing.xs * 2 +
                  LABEL_FIT_BUFFER +
                  LABEL_WIDTH_SLACK;
              }
            }
            const segmentStyle = [
              styles.segment,
              {
                flexGrow: weight,
                flexBasis: 0,
                backgroundColor: segment.color,
                minWidth: segmentMinWidth,
              },
            ];
            const contentColor = segment.labelColor ?? theme.colors.onSurface;
            const label =
              hasLabel || hasIcon ? (
                <View style={styles.segmentContent}>
                  {hasLabel ? (
                    <Text
                      numberOfLines={1}
                      ellipsizeMode="tail"
                      style={[theme.fonts.labelLarge, styles.segmentLabel, { color: contentColor }]}
                    >
                      {segment.label}
                    </Text>
                  ) : null}
                  {segment.icon ? (
                    <AppIcon name={segment.icon} size={SEGMENT_ICON_SIZE} color={contentColor} />
                  ) : null}
                </View>
              ) : null;

            return segment.onPress ? (
              <Pressable
                key={segment.id}
                testID={`expressive-progress-segment-${segment.id}`}
                onPress={segment.onPress}
                accessibilityLabel={segment.accessibilityLabel}
                accessibilityRole={segment.accessibilityRole}
                accessibilityState={segment.accessibilityState}
                style={segmentStyle}
              >
                {label}
              </Pressable>
            ) : (
              <View
                key={segment.id}
                testID={`expressive-progress-segment-${segment.id}`}
                style={segmentStyle}
              >
                {label}
              </View>
            );
          })}
        </View>
      ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    width: '100%',
    overflow: 'hidden',
  },
  measureLayer: {
    position: 'absolute',
    top: 0,
    left: 0,
    width: 9999,
    opacity: 0,
    alignItems: 'flex-start',
  },
  track: {
    width: '100%',
    overflow: 'hidden',
  },
  row: {
    flexDirection: 'row',
    width: '100%',
    height: '100%',
  },
  segment: {
    alignItems: 'center',
    height: '100%',
    justifyContent: 'center',
    minWidth: 0,
    overflow: 'hidden',
    paddingHorizontal: TOKENS.spacing.xs,
  },
  segmentContent: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: CONTENT_GAP,
    maxWidth: '100%',
  },
  // Font comes from theme.fonts.labelLarge (the same typography Paper buttons
  // use), applied inline; this only holds the layout-only overrides.
  segmentLabel: {
    flexShrink: 1,
  },
  // Same typography as segmentLabel but unconstrained, so onLayout reports the
  // label's true single-line width instead of a clipped one.
  measureLabel: {
    alignSelf: 'flex-start',
  },
});

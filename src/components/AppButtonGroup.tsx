import React, { useEffect } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';
import { Icon, Text, useTheme } from 'react-native-paper';
import { TOKENS } from '@/theme/tokens';
import { usePressAnimation } from '@/hooks/usePressAnimation';

export interface ButtonGroupOption {
  label: string;
  value: string;
  icon?: string;
}

interface AppButtonGroupProps {
  options: ButtonGroupOption[];
  value: string;
  onChange: (value: string) => void;
  accessibilityLabel?: string;
}

/**
 * Expressive connected button group (segmented selector). Segments sit in a
 * connected row; the selected segment morphs to a rounder shape and fills with
 * the secondary container color, with a spring press response on each segment.
 */
export function AppButtonGroup({ options, value, onChange, accessibilityLabel }: AppButtonGroupProps) {
  return (
    <View style={styles.row} accessibilityRole="radiogroup" accessibilityLabel={accessibilityLabel}>
      {options.map((option) => (
        <Segment
          key={option.value}
          option={option}
          selected={option.value === value}
          onPress={() => onChange(option.value)}
        />
      ))}
    </View>
  );
}

function Segment({
  option,
  selected,
  onPress,
}: {
  option: ButtonGroupOption;
  selected: boolean;
  onPress: () => void;
}) {
  const theme = useTheme();
  const { animatedStyle, onPressIn, onPressOut } = usePressAnimation();
  const radius = useSharedValue(selected ? TOKENS.radius.xxl : TOKENS.radius.sm);

  useEffect(() => {
    radius.value = withSpring(
      selected ? TOKENS.radius.xxl : TOKENS.radius.sm,
      TOKENS.motion.spring.expressive,
    );
  }, [selected, radius]);

  const radiusStyle = useAnimatedStyle(() => ({ borderRadius: radius.value }));

  const bg = selected ? theme.colors.secondaryContainer : theme.colors.surfaceVariant;
  const fg = selected ? theme.colors.onSecondaryContainer : theme.colors.onSurfaceVariant;

  return (
    <Animated.View style={[styles.segmentWrap, animatedStyle]}>
      <Animated.View style={[styles.segmentShape, radiusStyle, { backgroundColor: bg }]}>
        <Pressable
          onPress={onPress}
          onPressIn={onPressIn}
          onPressOut={onPressOut}
          accessibilityRole="radio"
          accessibilityState={{ selected }}
          accessibilityLabel={option.label}
          style={styles.segmentPress}
        >
          {option.icon ? <Icon source={option.icon} size={TOKENS.iconSize.sm} color={fg} /> : null}
          <Text
            variant="labelLarge"
            numberOfLines={1}
            style={{
              color: fg,
              fontWeight: selected
                ? TOKENS.typography.weight.bold
                : TOKENS.typography.weight.medium,
            }}
          >
            {option.label}
          </Text>
        </Pressable>
      </Animated.View>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    gap: TOKENS.spacing.xs,
    width: '100%',
  },
  segmentWrap: {
    flex: 1,
  },
  segmentShape: {
    overflow: 'hidden',
    minHeight: TOKENS.touchTarget.min,
  },
  segmentPress: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: TOKENS.spacing.xs,
    minHeight: TOKENS.touchTarget.min,
    paddingHorizontal: TOKENS.spacing.md,
  },
});

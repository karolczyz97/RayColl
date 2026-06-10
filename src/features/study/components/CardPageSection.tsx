import React, { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import type { LayoutChangeEvent } from 'react-native';
import { Text } from 'react-native-paper';
import Animated, { useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';
import { AppIcon } from '@/components/AppIcon';
import { usePulseAnimation } from '@/hooks/usePulseAnimation';
import { TOKENS } from '@/theme/tokens';

export interface CardAudioIndicator {
  iconName: string;
  color: string;
}

interface CardPageSectionProps {
  pageName: string;
  pageContent: string;
  isRevealed: boolean;
  isPrimary: boolean;
  hasBorderTop: boolean;
  borderColor: string;
  primaryColor: string;
  textColor: string;
  labelColor: string;
  fontSize: number;
  audioIndicator?: CardAudioIndicator | null;
  onLayout?: (event: LayoutChangeEvent) => void;
}

export function CardPageSection({
  pageName,
  pageContent,
  isRevealed,
  isPrimary,
  hasBorderTop,
  borderColor,
  primaryColor,
  textColor,
  labelColor,
  fontSize,
  audioIndicator,
  onLayout,
}: CardPageSectionProps) {
  const opacity = useSharedValue(isRevealed ? 1 : 0);
  const pulseStyle = usePulseAnimation(!!audioIndicator);

  useEffect(() => {
    opacity.value = withSpring(isRevealed ? 1 : 0, {
      damping: 20,
      stiffness: 120,
      mass: 0.8,
    });
  }, [isRevealed, opacity]);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
  }));

  return (
    <View
      onLayout={onLayout}
      style={[styles.pageRow, hasBorderTop && styles.pageRowBorder, { borderColor }]}
    >
      {audioIndicator ? (
        <Animated.View style={[styles.audioIndicator, pulseStyle]}>
          <AppIcon name={audioIndicator.iconName} size={TOKENS.iconSize.sm} color={audioIndicator.color} />
        </Animated.View>
      ) : null}
      <Text style={[styles.pageLabel, { color: labelColor }]}>{pageName}</Text>
      {/* Always render the content so a hidden page reserves its real height —
          revealing only fades it in (opacity), so the layout never reflows/jumps.
          Hidden content is removed from the a11y tree so it isn't read aloud. */}
      <Animated.View
        style={[styles.cardTextWrapper, animatedStyle]}
        accessibilityElementsHidden={!isRevealed}
        importantForAccessibility={isRevealed ? 'auto' : 'no-hide-descendants'}
      >
        <Text
          style={[
            styles.cardText,
            { color: isPrimary ? primaryColor : textColor, fontSize, lineHeight: Math.round(fontSize * 1.25) },
          ]}
        >
          {pageContent || '-'}
        </Text>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  pageRow: {
    // Grow to split leftover card space evenly between pages when the content
    // is shorter than the viewport; with overflowing content this is a no-op.
    flexGrow: 1,
    justifyContent: 'center',
    alignItems: 'center',
    width: '100%',
    paddingVertical: TOKENS.spacing.md,
  },
  audioIndicator: {
    position: 'absolute',
    top: TOKENS.spacing.sm,
    right: TOKENS.spacing.sm,
    zIndex: 1,
  },
  pageRowBorder: {
    borderTopWidth: 1,
  },
  pageLabel: {
    fontSize: TOKENS.typography.size.xxs,
    textTransform: 'uppercase',
    letterSpacing: TOKENS.typography.letterSpacing.wide,
    marginBottom: TOKENS.spacing.sm,
    fontWeight: TOKENS.typography.weight.bold,
  },
  cardTextWrapper: {
    width: '100%',
  },
  cardText: {
    textAlign: 'center',
    fontWeight: TOKENS.typography.weight.bold,
  },
});

import React, { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import { Text } from 'react-native-paper';
import Animated, { useAnimatedStyle, useSharedValue, withSpring } from 'react-native-reanimated';
import { TOKENS } from '../../../theme/tokens';

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
}: CardPageSectionProps) {
  const opacity = useSharedValue(isRevealed ? 1 : 0);

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
    <View style={[styles.pageRow, hasBorderTop && styles.pageRowBorder, { borderColor }]}>
      <Text style={[styles.pageLabel, { color: labelColor }]}>{pageName}</Text>
      <Animated.View style={animatedStyle}>
        <Text
          variant="headlineMedium"
          style={[styles.cardText, { color: isPrimary ? primaryColor : textColor }]}
        >
          {isRevealed ? pageContent || '-' : ' '}
        </Text>
      </Animated.View>
    </View>
  );
}

const styles = StyleSheet.create({
  pageRow: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    width: '100%',
    paddingVertical: TOKENS.spacing.md,
  },
  pageRowBorder: {
    borderTopWidth: 1,
  },
  pageLabel: {
    fontSize: TOKENS.typography.size.xxs,
    textTransform: 'uppercase',
    letterSpacing: 1.5,
    marginBottom: TOKENS.spacing.sm,
    fontWeight: TOKENS.typography.weight.bold,
  },
  cardText: {
    textAlign: 'center',
    fontWeight: TOKENS.typography.weight.bold,
  },
});

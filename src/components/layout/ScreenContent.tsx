import React from 'react';
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import { TOKENS } from '../../theme/tokens';

interface ScreenContentProps {
  children: React.ReactNode;
  maxWidth?: number;
  style?: StyleProp<ViewStyle>;
}

export function ScreenContent({ children, maxWidth, style }: ScreenContentProps) {
  return <View style={[styles.content, maxWidth ? { maxWidth } : undefined, style]}>{children}</View>;
}

const styles = StyleSheet.create({
  content: {
    width: '100%',
    alignSelf: 'center',
    gap: TOKENS.spacing.lg,
    padding: TOKENS.spacing.xxs,
  },
});

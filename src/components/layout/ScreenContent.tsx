import React from 'react';
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import { TOKENS } from '@/theme/tokens';

interface ScreenContentProps {
  children: React.ReactNode;
  maxWidth?: number;
  fill?: boolean;
  style?: StyleProp<ViewStyle>;
}

export function ScreenContent({ children, maxWidth, fill = false, style }: ScreenContentProps) {
  return (
    <View
      style={[styles.content, fill && styles.fill, maxWidth ? { maxWidth } : undefined, style]}
    >
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  content: {
    width: '100%',
    alignSelf: 'center',
    gap: TOKENS.spacing.lg,
    // Horizontal padding lives in AppScreen's contentRegion — not here.
    paddingVertical: TOKENS.spacing.sm,
  },
  fill: {
    flex: 1,
    minHeight: 0,
  },
});

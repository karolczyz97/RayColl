import React from 'react';
import { StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import { TOKENS } from '../../theme/tokens';

interface AppFormRowProps {
  children: React.ReactNode;
  style?: StyleProp<ViewStyle>;
}

export function AppFormRow({ children, style }: AppFormRowProps) {
  return <View style={[styles.row, style]}>{children}</View>;
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: TOKENS.spacing.sm,
    width: '100%',
  },
});

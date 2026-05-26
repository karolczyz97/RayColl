import { StyleSheet } from 'react-native';
import type { DimensionValue, ViewStyle } from 'react-native';
import { TOKENS } from './tokens';

export const dialogStyles = StyleSheet.create<{ dialog: ViewStyle }>({
  dialog: {
    alignSelf: 'center',
    width: TOKENS.dialog.width as DimensionValue,
    maxWidth: TOKENS.dialog.maxWidth,
    borderRadius: TOKENS.radius.xl,
  },
});

import { StyleSheet } from 'react-native';
import { TOKENS } from './tokens';

/**
 * Shared container styles for Paper `Menu` dropdowns (AppSelect, AppSplitButton,
 * AppMenuButton). Dynamic bits (elevation background, transformOrigin, anchor
 * width, alignment) stay in each component; only the static, repeated pieces
 * live here.
 */
export const menuStyles = StyleSheet.create({
  menu: {
    marginTop: TOKENS.menu.gap,
  },
  menuContent: {
    borderRadius: TOKENS.radius.lg,
    overflow: 'hidden',
  },
  menuItem: {
    minHeight: TOKENS.menu.itemHeight,
    width: '100%',
    maxWidth: '100%',
  },
});

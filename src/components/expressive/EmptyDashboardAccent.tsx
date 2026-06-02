import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Icon, useTheme } from 'react-native-paper';

import { TOKENS } from '../../theme/tokens';
import { ExpressiveShapeSurface } from './ExpressiveShapeSurface';

export function EmptyDashboardAccent() {
  const theme = useTheme();

  return (
    <ExpressiveShapeSurface
      variant="blob"
      colorRole="primary"
      style={styles.root}
    >
      <View style={styles.content}>
        <Icon
          source="cards-outline"
          size={TOKENS.iconSize.xl}
          color={theme.colors.onPrimaryContainer}
        />
      </View>
    </ExpressiveShapeSurface>
  );
}

const styles = StyleSheet.create({
  root: {
    width: TOKENS.emptyState.accentWidth,
    height: TOKENS.emptyState.accentHeight,
    alignSelf: 'center',
    marginBottom: TOKENS.spacing.lg,
  },
  content: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});

import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
import { useI18n } from '../../i18n';
import { TOKENS } from '../../theme/tokens';
import { EmptyDashboardAccent } from '../expressive';

export function EmptyDashboardState() {
  const { t } = useI18n();
  const theme = useTheme();

  return (
    <View style={styles.emptyContainer}>
      <EmptyDashboardAccent />
      <Text style={{ color: theme.colors.onSurfaceVariant }}>{t('dashboard.no_groups')}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  emptyContainer: {
    alignItems: 'center',
    paddingVertical: TOKENS.spacing.xxl * 2,
  },
});

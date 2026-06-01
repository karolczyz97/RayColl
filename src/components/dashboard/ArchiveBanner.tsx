import React from 'react';
import { StyleSheet } from 'react-native';
import { Card, IconButton, useTheme } from 'react-native-paper';
import { router } from 'expo-router';
import { useI18n } from '../../i18n';
import { ROUTES } from '../../constants/routes';
import { TOKENS } from '../../theme/tokens';

interface Props {
  count: number;
}

export function ArchiveBanner({ count }: Props) {
  const { t } = useI18n();
  const theme = useTheme();

  return (
    <Card style={styles.card} onPress={() => router.push(ROUTES.ARCHIVE)}>
      <Card.Title
        title={t('archive.title')}
        titleStyle={{ color: theme.colors.onSurface }}
        subtitle={t('archive.decks_count', { count })}
        subtitleStyle={{ color: theme.colors.onSurfaceVariant }}
        left={(props) => <IconButton {...props} icon="archive-outline" />}
        right={(props) => <IconButton {...props} icon="chevron-right" />}
      />
    </Card>
  );
}

const styles = StyleSheet.create({
  card: {
    marginHorizontal: TOKENS.spacing.lg,
    marginBottom: TOKENS.spacing.lg,
    borderRadius: TOKENS.radius.lg,
  },
});

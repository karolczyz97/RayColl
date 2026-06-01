import React, { useEffect, useMemo, useState } from 'react';
import { StyleSheet } from 'react-native';
import { Button, Text, TouchableRipple, useTheme } from 'react-native-paper';
import type { FlashcardGroup } from '../../types/models';
import type { TranslationFn } from '../../i18n';
import { ARCHIVE_RETENTION_MS } from '../../constants/archive';
import { TOKENS } from '../../theme/tokens';
import { AppCard } from '../AppCard';

interface Props {
  group: FlashcardGroup;
  onPress: () => void;
  onRestore: () => void;
  onPermanentDelete: () => void;
  t: TranslationFn;
}

export function ArchivedDeckCard({
  group,
  onPress,
  onRestore,
  onPermanentDelete,
  t,
}: Props) {
  const theme = useTheme();
  const archivedAt = group.archivedAt ?? 0;

  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 60 * 1000);
    return () => clearInterval(timer);
  }, []);

  const daysLeft = useMemo(() => {
    const dayMs = 24 * 60 * 60 * 1000;
    return Math.max(
      0,
      Math.ceil((archivedAt + ARCHIVE_RETENTION_MS - now) / dayMs),
    );
  }, [archivedAt, now]);

  const expiryLabel =
    daysLeft <= 0
      ? t('archive.auto_delete_today')
      : daysLeft === 1
        ? t('archive.auto_delete_tomorrow')
        : t('archive.auto_delete_in', { days: daysLeft });

  const expiryColor =
    daysLeft <= 1
      ? theme.colors.error
      : daysLeft <= 3
        ? theme.colors.tertiary
        : theme.colors.onSurfaceVariant;

  return (
    <AppCard mode="elevated" style={styles.card}>
      <TouchableRipple onPress={onPress}>
        <AppCard.Content>
          <Text variant="titleMedium">{group.name}</Text>
          <Text variant="bodySmall" style={{ color: theme.colors.onSurfaceVariant }}>
            {t('archive.cards_count', { count: group.cards.length })}
          </Text>
          <Text variant="bodySmall" style={{ color: expiryColor }}>
            {expiryLabel}
          </Text>
        </AppCard.Content>
      </TouchableRipple>
      <AppCard.Actions style={styles.actions}>
        <Button mode="contained-tonal" onPress={onRestore}>
          {t('btn.restore')}
        </Button>
        <Button textColor={theme.colors.error} onPress={onPermanentDelete}>
          {t('archive.delete_permanently')}
        </Button>
      </AppCard.Actions>
    </AppCard>
  );
}

const styles = StyleSheet.create({
  card: {
    marginBottom: TOKENS.spacing.sm,
  },
  actions: {
    justifyContent: 'flex-end',
    gap: TOKENS.spacing.sm,
  },
});

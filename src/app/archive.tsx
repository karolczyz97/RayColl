import React, { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
import { router } from 'expo-router';
import { useFlashcardStore } from '../hooks/useFlashcardStore';
import { useI18n } from '../i18n';
import { AppScreen } from '../components/layout/AppScreen';
import { ConfirmDialog } from '../components/dialogs/ConfirmDialog';
import { ArchivedDeckCard } from '../components/archive/ArchivedDeckCard';
import { TOKENS } from '../theme/tokens';
import { ROUTES } from '../constants/routes';
import { safeBack } from '../utils/navigation';
import type { FlashcardGroup } from '../types/models';

export default function ArchiveScreen() {
  const { t } = useI18n();
  const theme = useTheme();
  const store = useFlashcardStore();
  const [pendingDelete, setPendingDelete] = useState<{ id: string; name: string } | null>(null);
  const [pendingRestore, setPendingRestore] = useState<{ id: string; name: string } | null>(null);

  const archived = store.archivedGroups;

  const handleRestore = () => {
    if (pendingRestore) {
      store.restoreGroup(pendingRestore.id);
      setPendingRestore(null);
    }
  };

  const handlePermanentDelete = () => {
    if (pendingDelete) {
      store.deleteGroup(pendingDelete.id);
      setPendingDelete(null);
    }
  };

  return (
    <AppScreen title={t('archive.title')} onBack={safeBack}>
      {archived.length === 0 ? (
        <View style={styles.empty}>
          <Text style={{ color: theme.colors.onSurfaceVariant }}>
            {t('archive.empty')}
          </Text>
        </View>
      ) : (
        <View style={styles.list}>
          {archived.map((group: FlashcardGroup) => (
            <ArchivedDeckCard
              key={group.id}
              group={group}
              onPress={() => router.push(ROUTES.browseDeck(group.id))}
              onRestore={() => setPendingRestore({ id: group.id, name: group.name })}
              onPermanentDelete={() => setPendingDelete({ id: group.id, name: group.name })}
              t={t}
            />
          ))}
        </View>
      )}

      <ConfirmDialog
        visible={!!pendingRestore}
        onDismiss={() => setPendingRestore(null)}
        onConfirm={handleRestore}
        title={pendingRestore ? t('dialog.restore.title', { name: pendingRestore.name }) : ''}
        message={pendingRestore ? t('dialog.restore.desc', { name: pendingRestore.name }) : ''}
        confirmLabel={t('btn.restore')}
        cancelLabel={t('btn.cancel')}
      />

      <ConfirmDialog
        visible={!!pendingDelete}
        onDismiss={() => setPendingDelete(null)}
        onConfirm={handlePermanentDelete}
        destructive
        title={t('dialog.delete_permanent.title')}
        message={
          pendingDelete
            ? t('dialog.delete_permanent.desc', { name: pendingDelete.name })
            : ''
        }
        confirmLabel={t('archive.delete_permanently')}
        cancelLabel={t('btn.cancel')}
      />
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  list: {
    padding: TOKENS.spacing.lg,
  },
  empty: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: TOKENS.spacing.xl,
  },
});

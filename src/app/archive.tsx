import React, { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Text } from 'react-native-paper';

import { safeBack } from '@/utils/navigation';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { useI18n } from '@/i18n';
import { useResponsiveLayout } from '@/hooks/useResponsiveLayout';
import { AppScreen } from '@/components/layout/AppScreen';
import { AnimatedSection } from '@/components/layout/AnimatedSection';
import { LoadingState } from '@/components/layout/LoadingState';
import { DeckGrid } from '@/components/dashboard/DeckGrid';
import { GroupCard } from '@/components/dashboard/GroupCard';
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog';
import { TOKENS } from '@/theme/tokens';
import type { FlashcardGroup } from '@/types/models';
import {
  getArchiveCountdownLabelKey,
  getArchiveDaysRemaining,
} from '@/features/archive/archiveCountdown';

export default function ArchivePage() {
  const { t } = useI18n();
  const store = useFlashcardStore();
  const { contentMaxWidth } = useResponsiveLayout();
  const { archivedGroups, restoreGroup, deleteGroup, isLoading } = store;

  const [restoreTarget, setRestoreTarget] = useState<FlashcardGroup | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<FlashcardGroup | null>(null);
  // Snapshot "now" once at mount: the countdown is day-granular, so a stable
  // reference avoids calling Date.now() during render (purity rule).
  const [now] = useState(() => Date.now());

  if (isLoading) {
    return <LoadingState />;
  }

  const confirmRestore = () => {
    const target = restoreTarget;
    setRestoreTarget(null);
    if (target) {
      void restoreGroup(target.id);
    }
  };

  const confirmDelete = () => {
    const target = deleteTarget;
    setDeleteTarget(null);
    if (target) {
      void deleteGroup(target.id);
    }
  };

  const renderArchivedCard = (group: FlashcardGroup) => {
    const daysRemaining = getArchiveDaysRemaining(group.archivedAt ?? 0, now);
    const expiryLabel = t(getArchiveCountdownLabelKey(daysRemaining), { days: daysRemaining });
    return (
      <GroupCard
        group={group}
        variant="archived"
        expiryLabel={expiryLabel}
        onRestore={() => setRestoreTarget(group)}
        onDeletePermanently={() => setDeleteTarget(group)}
      />
    );
  };

  return (
    <AppScreen title={t('archive.title')} onBack={safeBack} maxWidth={contentMaxWidth}>
      {archivedGroups.length === 0 ? (
        <AnimatedSection order={0}>
          <View style={styles.empty}>
            <Text variant="bodyLarge">{t('archive.empty')}</Text>
          </View>
        </AnimatedSection>
      ) : (
        <DeckGrid groups={archivedGroups} renderItem={renderArchivedCard} baseOrder={0} />
      )}

      <ConfirmDialog
        visible={restoreTarget !== null}
        title={t('dialog.restore.title', { name: restoreTarget?.name ?? '' })}
        message={t('dialog.restore.desc', { name: restoreTarget?.name ?? '' })}
        confirmLabel={t('btn.restore')}
        cancelLabel={t('btn.cancel')}
        onConfirm={confirmRestore}
        onDismiss={() => setRestoreTarget(null)}
      />

      <ConfirmDialog
        visible={deleteTarget !== null}
        title={t('dialog.delete_permanent.title')}
        message={t('dialog.delete_permanent.desc')}
        confirmLabel={t('btn.delete')}
        cancelLabel={t('btn.cancel')}
        destructive
        onConfirm={confirmDelete}
        onDismiss={() => setDeleteTarget(null)}
      />
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  empty: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: TOKENS.spacing.xxl,
  },
});

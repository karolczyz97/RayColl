import React, { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Divider, IconButton, List } from 'react-native-paper';
import { router } from 'expo-router';
import type { StudyMode } from '@/types/models';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { useI18n } from '@/i18n';
import { getModeName } from '@/i18n/modeHelpers';
import { useResponsiveLayout } from '@/hooks/useResponsiveLayout';
import { navigateUp } from '@/utils/navigation';
import { ROUTES } from '@/constants/routes';
import { TOKENS } from '@/theme/tokens';
import { AppScreen } from '@/components/layout/AppScreen';
import { AnimatedSection } from '@/components/layout/AnimatedSection';
import { SectionCard } from '@/components/layout/SectionCard';
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog';

export function StudyModesScreen() {
  const { t } = useI18n();
  const store = useFlashcardStore();
  const { contentMaxWidth } = useResponsiveLayout();
  const [modeToDelete, setModeToDelete] = useState<StudyMode | null>(null);

  const handleDeleteConfirm = () => {
    if (modeToDelete) {
      store.deleteStudyMode(modeToDelete.id);
    }
    setModeToDelete(null);
  };

  return (
    <AppScreen title={t('study_modes.title')} onBack={navigateUp} maxWidth={contentMaxWidth}>
      <AnimatedSection order={0}>
        <SectionCard>
          {store.studyModes.map((mode, index) => (
            <View key={mode.id}>
              {index > 0 && <Divider />}
              <List.Item
                style={styles.listItem}
                title={getModeName(t, mode.id, mode.name)}
                description={
                  t('study_modes.steps_count', { count: mode.steps.length }) +
                  (mode.isBuiltIn ? ` · ${t('study_modes.built_in')}` : '')
                }
                onPress={() => router.navigate(ROUTES.studyMode(mode.id))}
                right={() => (
                  <View style={styles.itemActions}>
                    {!mode.isBuiltIn && (
                      <IconButton
                        icon="delete-outline"
                        size={TOKENS.iconSize.md}
                        onPress={() => setModeToDelete(mode)}
                        accessibilityLabel={t('study_modes.delete_title')}
                      />
                    )}
                    <List.Icon icon="chevron-right" />
                  </View>
                )}
              />
            </View>
          ))}
        </SectionCard>
      </AnimatedSection>

      <ConfirmDialog
        visible={!!modeToDelete}
        onDismiss={() => setModeToDelete(null)}
        onConfirm={handleDeleteConfirm}
        title={t('study_modes.delete_title')}
        message={t('study_modes.delete_message', {
          name: modeToDelete ? getModeName(t, modeToDelete.id, modeToDelete.name) : '',
        })}
        confirmLabel={t('study_modes.delete_title')}
        cancelLabel={t('btn.cancel')}
        destructive
      />
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  listItem: {
    paddingVertical: TOKENS.spacing.xs,
    paddingHorizontal: 0,
  },
  itemActions: {
    flexDirection: 'row',
    alignItems: 'center',
  },
});

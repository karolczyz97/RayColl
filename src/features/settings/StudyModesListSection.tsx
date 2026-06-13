import React, { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Button, Divider, IconButton, List, useTheme } from 'react-native-paper';
import { router } from 'expo-router';
import type { ModeStep, StudyMode } from '@/types/models';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { useI18n } from '@/i18n';
import { getModeName } from '@/i18n/modeHelpers';
import { uid } from '@/utils/id';
import { ROUTES } from '@/constants/routes';
import { MAX_VISIBLE_PAGE_COUNT } from '@/constants/pages';
import { TOKENS } from '@/theme/tokens';
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog';
import { CreateStudyModeSection } from '@/components/settings/CreateStudyModeSection';
import { AddStepDialog } from '@/components/settings/AddStepDialog';
import { formatStepSummary } from './studyModeUtils';
import { useStepEditorController } from './useStepEditorController';

export function StudyModesListSection() {
  const { t } = useI18n();
  const theme = useTheme();
  const store = useFlashcardStore();
  const [modeToDelete, setModeToDelete] = useState<StudyMode | null>(null);

  const [creatingMode, setCreatingMode] = useState(false);
  const [newModeName, setNewModeName] = useState('');
  const [customSteps, setCustomSteps] = useState<ModeStep[]>([]);
  const [editingModeId, setEditingModeId] = useState<string | null>(null);

  const stepEditor = useStepEditorController({
    pageCount: MAX_VISIBLE_PAGE_COUNT,
    creatingMode,
    editingModeId,
    customSteps,
    setCustomSteps,
    setEditingModeId,
  });

  const closeCreateModeDialog = () => {
    setCreatingMode(false);
    setEditingModeId(null);
    setCustomSteps([]);
    setNewModeName('');
  };

  const saveCustomMode = () => {
    if (!newModeName.trim() || customSteps.length === 0) return;
    store.addStudyMode({
      id: uid(),
      name: newModeName.trim(),
      steps: customSteps,
      isBuiltIn: false,
    });
    closeCreateModeDialog();
  };

  const handleDeleteConfirm = () => {
    if (modeToDelete) {
      store.deleteStudyMode(modeToDelete.id);
    }
    setModeToDelete(null);
  };

  return (
    <>
      <View style={styles.modeList}>
        {store.studyModes.map((mode, index) => (
          <View key={mode.id}>
            {index > 0 && <Divider />}
            <List.Item
              style={styles.listItem}
              title={getModeName(t, mode.id, mode.name)}
              description={
                t('study_modes.steps_count', { count: mode.steps.length }) +
                (mode.isBuiltIn ? ` - ${t('study_modes.built_in')}` : '')
              }
              onPress={() => router.navigate(ROUTES.studyMode(mode.id))}
              right={() => (
                <View style={styles.itemActions}>
                  {!mode.isBuiltIn && (
                    <IconButton
                      icon="delete-outline"
                      size={TOKENS.iconSize.md}
                      iconColor={theme.colors.error}
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
      </View>
      <View style={styles.footer}>
        <Button
          icon="plus"
          mode="text"
          onPress={() => {
            setEditingModeId(null);
            setCreatingMode(true);
          }}
          accessibilityLabel={t('settings.create_mode_btn')}
        >
          {t('settings.create_mode_btn')}
        </Button>
      </View>

      <CreateStudyModeSection
        visible={creatingMode}
        onDismiss={closeCreateModeDialog}
        newModeName={newModeName}
        setNewModeName={setNewModeName}
        customSteps={customSteps}
        setCustomSteps={setCustomSteps}
        saveCustomMode={saveCustomMode}
        setStepDialogOpen={stepEditor.setStepDialogOpen}
        setEditingModeId={setEditingModeId}
        formatStepSummary={formatStepSummary}
      />

      <AddStepDialog
        visible={stepEditor.stepDialogOpen}
        onDismiss={() => stepEditor.setStepDialogOpen(false)}
        newStepType={stepEditor.newStepType}
        setNewStepType={stepEditor.setNewStepType}
        newPageIdx={stepEditor.newPageIdx}
        pageCount={MAX_VISIBLE_PAGE_COUNT}
        setNewPageIdx={stepEditor.setNewPageIdx}
        newMs={stepEditor.newMs}
        setNewMs={stepEditor.setNewMs}
        newPauseMultiplier={stepEditor.newPauseMultiplier}
        setNewPauseMultiplier={stepEditor.setNewPauseMultiplier}
        newThreshold={stepEditor.newThreshold}
        setNewThreshold={stepEditor.setNewThreshold}
        newCondition={stepEditor.newCondition}
        setNewCondition={stepEditor.setNewCondition}
        confirmAddStep={stepEditor.confirmAddStep}
        stepLabels={stepEditor.stepLabels}
      />

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
    </>
  );
}

const styles = StyleSheet.create({
  modeList: {
    marginHorizontal: -TOKENS.spacing.lg,
    borderRadius: TOKENS.control.borderRadius,
    overflow: 'hidden',
  },
  listItem: {
    paddingVertical: TOKENS.spacing.sm,
    paddingHorizontal: TOKENS.spacing.lg,
  },
  itemActions: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  footer: {
    flexDirection: 'row',
    marginTop: TOKENS.spacing.sm,
  },
});

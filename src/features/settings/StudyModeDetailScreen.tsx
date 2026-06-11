import React, { useCallback, useState } from 'react';
import { useLocalSearchParams } from 'expo-router';
import type { ModeStep } from '@/types/models';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { useI18n } from '@/i18n';
import { getModeName } from '@/i18n/modeHelpers';
import { useResponsiveLayout } from '@/hooks/useResponsiveLayout';
import { navigateUp } from '@/utils/navigation';
import { MAX_VISIBLE_PAGE_COUNT } from '@/constants/pages';
import { AppScreen } from '@/components/layout/AppScreen';
import { AnimatedSection } from '@/components/layout/AnimatedSection';
import { StudyModeEditor } from '@/components/settings/StudyModeEditor';
import { AddStepDialog } from '@/components/settings/AddStepDialog';
import { formatStepSummary, getModeCustomization } from './studyModeUtils';
import { useStepEditorController } from './useStepEditorController';

const NO_DRAFT_STEPS: ModeStep[] = [];

export function StudyModeDetailScreen() {
  const { modeId } = useLocalSearchParams<{ modeId: string }>();
  const { t } = useI18n();
  const store = useFlashcardStore();
  const { contentMaxWidth } = useResponsiveLayout();
  const [editingModeId, setEditingModeId] = useState<string | null>(null);
  // Ekran edytuje istniejący tryb — tryb „szkicu" z dialogu tworzenia nie występuje.
  const setNoDraftSteps = useCallback(() => {}, []);

  const stepEditor = useStepEditorController({
    // Tryby są globalne (nie znamy talii), więc strony można wskazać do limitu aplikacji.
    pageCount: MAX_VISIBLE_PAGE_COUNT,
    creatingMode: false,
    editingModeId,
    customSteps: NO_DRAFT_STEPS,
    setCustomSteps: setNoDraftSteps,
    setEditingModeId,
  });

  const mode = store.studyModes.find((item) => item.id === modeId) ?? null;

  if (!mode) {
    // Tryb mógł zostać właśnie usunięty — pokaż pusty ekran z powrotem.
    return (
      <AppScreen title={t('study_modes.title')} onBack={navigateUp} maxWidth={contentMaxWidth}>
        {null}
      </AppScreen>
    );
  }

  const { isDefaultMode, hasCustomSteps } = getModeCustomization(mode);

  return (
    <AppScreen
      title={getModeName(t, mode.id, mode.name)}
      onBack={navigateUp}
      maxWidth={contentMaxWidth}
    >
      <AnimatedSection order={0}>
        <StudyModeEditor
          mode={mode}
          isDefaultMode={isDefaultMode}
          hasCustomSteps={hasCustomSteps}
          moveStep={stepEditor.moveStep}
          deleteStep={stepEditor.deleteStep}
          addStepToMode={stepEditor.addStepToMode}
          onResetMode={stepEditor.resetMode}
          onRenameMode={stepEditor.renameMode}
          formatStepSummary={formatStepSummary}
        />
      </AnimatedSection>

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
        confirmAddStep={stepEditor.confirmAddStep}
        stepLabels={stepEditor.stepLabels}
      />
    </AppScreen>
  );
}

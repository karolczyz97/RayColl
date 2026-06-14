import { useState, useMemo } from 'react';
import type { ModeStep, StepCondition, StudyMode } from '@/types/models';
import { swapElements } from '@/utils/array';
import { uid } from '@/utils/id';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { useI18n } from '@/i18n';
import { buildModeStep } from './buildModeStep';

interface UseStepEditorControllerParams {
  pageCount: number;
  creatingMode: boolean;
  editingModeId: string | null;
  customSteps: ModeStep[];
  setCustomSteps: React.Dispatch<React.SetStateAction<ModeStep[]>>;
  setEditingModeId: (v: string | null) => void;
}

export function useStepEditorController({
  pageCount,
  creatingMode,
  editingModeId,
  customSteps,
  setCustomSteps,
  setEditingModeId,
}: UseStepEditorControllerParams) {
  const store = useFlashcardStore();
  const { t } = useI18n();
  const [stepDialogOpen, setStepDialogOpen] = useState(false);
  const [newStepType, setNewStepType] = useState<string>('show_page');
  const [newPageIdx, setNewPageIdx] = useState(0);
  const [newMs, setNewMs] = useState(500);
  const [newPauseMultiplier, setNewPauseMultiplier] = useState(1);
  const [newThreshold, setNewThreshold] = useState(70);
  const [newCondition, setNewCondition] = useState<'always' | StepCondition>('always');

  const moveStep = (mode: StudyMode, index: number, direction: -1 | 1) => {
    const target = index + direction;
    if (target < 0 || target >= mode.steps.length) return;
    store.updateStudyMode({ ...mode, steps: swapElements(mode.steps, index, target) });
  };

  const deleteStep = (mode: StudyMode, index: number) => {
    store.updateStudyMode({ ...mode, steps: mode.steps.filter((_, stepIndex) => stepIndex !== index) });
  };

  const addStepToMode = (mode: StudyMode) => {
    setEditingModeId(mode.id);
    setStepDialogOpen(true);
  };

  const resetMode = (mode: StudyMode) => {
    store.resetStudyMode(mode.id);
  };

  const renameMode = (mode: StudyMode, name: string) => {
    const trimmed = name.trim();
    if (!trimmed || trimmed === mode.name) return;
    store.updateStudyMode({ ...mode, name: trimmed });
  };

  const confirmAddStep = () => {
    const step = buildModeStep({
      id: uid(),
      newStepType,
      pageCount,
      newPageIdx,
      newMs,
      newPauseMultiplier,
      newThreshold,
      newCondition,
    });
    if (!step) return;

    if (editingModeId && !creatingMode) {
      const mode = store.studyModes.find((item) => item.id === editingModeId);
      if (mode) {
        store.updateStudyMode({ ...mode, steps: [...mode.steps, step] });
      }
    } else {
      setCustomSteps([...customSteps, step]);
    }

    setStepDialogOpen(false);
    setEditingModeId(null);
    // Warunek nie jest "lepki" — kolejny krok domyślnie wykonuje się zawsze.
    setNewCondition('always');
  };

  const stepLabels = useMemo<Record<string, string>>(
    () => ({
      show_page: t('step.type.show_page'),
      rate: t('step.type.rate'),
      speak_page: t('step.type.speak_page'),
      dynamic_pause: t('step.type.dynamic_pause'),
      wait: t('step.type.wait'),
      listen_and_branch: t('step.type.listen_and_branch'),
      listen_and_check: t('step.type.listen_and_check'),
      next_card: t('step.type.next_card'),
    }),
    [t],
  );

  return {
    stepDialogOpen,
    setStepDialogOpen,
    newStepType,
    setNewStepType,
    newPageIdx,
    setNewPageIdx,
    newMs,
    setNewMs,
    newPauseMultiplier,
    setNewPauseMultiplier,
    newThreshold,
    setNewThreshold,
    newCondition,
    setNewCondition,
    moveStep,
    deleteStep,
    addStepToMode,
    resetMode,
    renameMode,
    confirmAddStep,
    stepLabels,
  };
}

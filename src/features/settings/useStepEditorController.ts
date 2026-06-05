import { useState, useMemo } from 'react';
import type { ModeStep, StudyMode } from '@/types/models';
import { swapElements } from '@/utils/array';
import { uid } from '@/utils/id';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { useI18n } from '@/i18n';

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
  const [newThreshold, setNewThreshold] = useState(70);

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

  const confirmAddStep = () => {
    let step: ModeStep;
    const safePageIdx = Math.max(0, Math.min(pageCount - 1, Math.trunc(newPageIdx)));
    switch (newStepType) {
      case 'show_page':
        step = { id: uid(), type: 'show_page', pageIndex: safePageIdx };
        break;
      case 'speak_page':
        step = { id: uid(), type: 'speak_page', pageIndex: safePageIdx, extraPauseMs: newMs };
        break;
      case 'dynamic_pause':
        step = { id: uid(), type: 'dynamic_pause', nextPageIndex: safePageIdx, extraPauseMs: newMs };
        break;
      case 'wait':
        step = { id: uid(), type: 'wait', ms: newMs };
        break;
      case 'listen_and_branch':
        step = { id: uid(), type: 'listen_and_branch', pageIndex: safePageIdx, successThreshold: newThreshold };
        break;
      case 'reveal_on_tap':
        step = { id: uid(), type: 'reveal_on_tap' };
        break;
      case 'rate':
        step = { id: uid(), type: 'rate' };
        break;
      default:
        return;
    }

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
  };

  const stepLabels = useMemo<Record<string, string>>(
    () => ({
      show_page: t('step.type.show_page'),
      reveal_on_tap: t('step.type.reveal_on_tap'),
      rate: t('step.type.rate'),
      speak_page: t('step.type.speak_page'),
      dynamic_pause: t('step.type.dynamic_pause'),
      wait: t('step.type.wait'),
      listen_and_branch: t('step.type.listen_and_branch'),
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
    newThreshold,
    setNewThreshold,
    moveStep,
    deleteStep,
    addStepToMode,
    resetMode,
    confirmAddStep,
    stepLabels,
  };
}

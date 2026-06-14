import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ModeStep, StepCondition, StudyMode } from '@/types/models';
import { STUDY_MODE_NEW_ID } from '@/constants/routes';
import { MAX_VISIBLE_PAGE_COUNT } from '@/constants/pages';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { createSeedModes } from '@/store/seed/seedModes';
import { normalizeStudyMode } from '@/store/storeDataNormalization';
import { useI18n } from '@/i18n';
import { swapElements } from '@/utils/array';
import { deepEqual } from '@/utils/deepEqual';
import { uid } from '@/utils/id';
import { buildModeStep } from './buildModeStep';
import { hasBlockingStepIssue, validateModeSteps } from './validateModeSteps';

function cloneStep(step: ModeStep): ModeStep {
  return { ...step };
}

function cloneStudyMode(mode: StudyMode): StudyMode {
  return {
    ...mode,
    steps: mode.steps.map(cloneStep),
  };
}

function createDraftMode(): StudyMode {
  return {
    id: uid(),
    name: '',
    steps: [],
    isBuiltIn: false,
  };
}

function getSeedSteps(mode: StudyMode): ModeStep[] | null {
  if (!mode.isBuiltIn) return null;
  const sourceId = mode.builtInSourceId ?? mode.id;
  const seed = createSeedModes().find((seedMode) => seedMode.id === sourceId);
  return seed ? seed.steps.map(cloneStep) : null;
}

const STEP_TYPE_ORDER: ModeStep['type'][] = [
  'show_page',
  'show_all_pages',
  'wait_for_tap_to_reveal_next',
  'wait_for_tap_to_reveal',
  'speak_page',
  'listen_and_check',
  'dynamic_pause',
  'wait',
  'feedback_success',
  'feedback_error',
  'show_ratings',
  'auto_rate_from_answer',
  'auto_rate_fixed',
  'mark_failed',
  'next_card',
];

const STEP_TYPE_LABEL_KEYS: Record<ModeStep['type'], string> = {
  show_page: 'step.type.show_page',
  show_all_pages: 'step.type.show_all_pages',
  wait_for_tap_to_reveal_next: 'step.type.wait_for_tap_to_reveal_next',
  wait_for_tap_to_reveal: 'step.type.wait_for_tap_to_reveal',
  speak_page: 'step.type.speak_page',
  listen_and_check: 'step.type.listen_and_check',
  dynamic_pause: 'step.type.dynamic_pause',
  wait: 'step.type.wait',
  feedback_success: 'step.type.feedback_success',
  feedback_error: 'step.type.feedback_error',
  show_ratings: 'step.type.show_ratings',
  auto_rate_from_answer: 'step.type.auto_rate_from_answer',
  auto_rate_fixed: 'step.type.auto_rate_fixed',
  mark_failed: 'step.type.mark_failed',
  next_card: 'step.type.next_card',
};

interface DraftState {
  initializedModeId: string | null;
  draft: StudyMode | null;
  original: StudyMode | null;
}

export function useStudyModeDraftController({
  modeId,
  selectForGroup,
  pageCount = MAX_VISIBLE_PAGE_COUNT,
}: {
  modeId?: string;
  selectForGroup?: string;
  pageCount?: number;
}) {
  const store = useFlashcardStore();
  const { t } = useI18n();
  const isCreate = modeId === STUDY_MODE_NEW_ID;
  const initializedModeIdRef = useRef<string | null>(null);
  const [draftState, setDraftState] = useState<DraftState>({
    initializedModeId: null,
    draft: null,
    original: null,
  });
  const { draft, original, initializedModeId } = draftState;

  const [stepDialogOpen, setStepDialogOpen] = useState(false);
  const [newStepType, setNewStepType] = useState<string>('show_page');
  const [newPageIdx, setNewPageIdx] = useState(0);
  const [newMs, setNewMs] = useState(500);
  const [newPauseMultiplier, setNewPauseMultiplier] = useState(1);
  const [newThreshold, setNewThreshold] = useState(70);
  const [newRating, setNewRating] = useState(3);
  const [newCondition, setNewCondition] = useState<'always' | StepCondition>('always');

  useEffect(() => {
    if (!modeId || initializedModeIdRef.current === modeId) return;

    if (modeId === STUDY_MODE_NEW_ID) {
      const nextDraft = createDraftMode();
      initializedModeIdRef.current = modeId;
      // eslint-disable-next-line react-hooks/set-state-in-effect -- route draft initializes once when the route id becomes available
      setDraftState({ initializedModeId: modeId, draft: nextDraft, original: null });
      return;
    }

    const mode = store.studyModes.find((item) => item.id === modeId);
    if (!mode && store.isLoading) return;

    initializedModeIdRef.current = modeId;
    const originalSnapshot = mode ? cloneStudyMode(mode) : null;
    setDraftState({
      initializedModeId: modeId,
      draft: originalSnapshot ? cloneStudyMode(originalSnapshot) : null,
      original: originalSnapshot,
    });
  }, [modeId, store.isLoading, store.studyModes]);

  const setName = useCallback((name: string) => {
    setDraftState((current) => ({
      ...current,
      draft: current.draft ? { ...current.draft, name } : current.draft,
    }));
  }, []);

  const moveStep = useCallback((_mode: StudyMode, index: number, direction: -1 | 1) => {
    setDraftState((current) => {
      if (!current.draft) return current;
      const nextSteps = swapElements(current.draft.steps, index, index + direction);
      return nextSteps === current.draft.steps
        ? current
        : { ...current, draft: { ...current.draft, steps: nextSteps } };
    });
  }, []);

  const deleteStep = useCallback((_mode: StudyMode, index: number) => {
    setDraftState((current) => ({
      ...current,
      draft: current.draft
        ? {
            ...current.draft,
            steps: current.draft.steps.filter((_, stepIndex) => stepIndex !== index),
          }
        : current.draft,
    }));
  }, []);

  const addStepToMode = useCallback(() => {
    setStepDialogOpen(true);
  }, []);

  const resetSteps = useCallback((_mode: StudyMode) => {
    setDraftState((current) => {
      if (!current.draft) return current;
      const seedSteps = getSeedSteps(current.draft);
      return seedSteps
        ? { ...current, draft: { ...current.draft, steps: seedSteps } }
        : current;
    });
  }, []);

  const confirmAddStep = useCallback(() => {
    const step = buildModeStep({
      id: uid(),
      newStepType,
      pageCount,
      newPageIdx,
      newMs,
      newPauseMultiplier,
      newThreshold,
      newRating,
      newCondition,
    });
    if (!step) return;

    setDraftState((current) => ({
      ...current,
      draft: current.draft
        ? { ...current.draft, steps: [...current.draft.steps, step] }
        : current.draft,
    }));
    setStepDialogOpen(false);
    setNewCondition('always');
  }, [
    newCondition,
    newMs,
    newPageIdx,
    newPauseMultiplier,
    newRating,
    newStepType,
    newThreshold,
    pageCount,
  ]);

  const isDirty = useMemo(() => {
    if (!draft) return false;
    if (isCreate) {
      return draft.name.trim().length > 0 || draft.steps.length > 0;
    }
    return original ? !deepEqual(draft, original) : false;
  }, [draft, isCreate, original]);

  const stepIssues = useMemo(
    () => (draft ? validateModeSteps(draft.steps, pageCount) : []),
    [draft, pageCount],
  );
  const isNameValid = !draft || draft.isBuiltIn || draft.name.trim().length > 0;
  const areStepsValid = !!draft && draft.steps.length >= 1;
  // Błędy walidacji (np. strona poza zakresem) blokują zapis; ostrzeżenia nie.
  const isValid = isNameValid && areStepsValid && !hasBlockingStepIssue(stepIssues);

  const save = useCallback(() => {
    if (!draft || !isValid) return false;
    const modeToSave: StudyMode = {
      ...draft,
      name: draft.isBuiltIn ? draft.name : draft.name.trim(),
      steps: draft.steps.map(cloneStep),
    };

    if (isCreate) {
      store.addStudyMode(modeToSave);
    } else {
      store.updateStudyMode(modeToSave);
    }

    if (selectForGroup) {
      store.setActiveStudyMode(selectForGroup, modeToSave.id);
    }

    const savedSnapshot = cloneStudyMode(normalizeStudyMode(modeToSave));
    setDraftState((current) => ({
      ...current,
      draft: cloneStudyMode(savedSnapshot),
      original: savedSnapshot,
    }));
    return true;
  }, [draft, isCreate, isValid, selectForGroup, store]);

  // Kolejność = kolejność w dropdownie. Tylko primitive steps — żadnych makro-kroków.
  const stepLabels = useMemo<Record<string, string>>(
    () =>
      Object.fromEntries(
        STEP_TYPE_ORDER.map((type) => [type, t(STEP_TYPE_LABEL_KEYS[type])]),
      ) as Record<string, string>,
    [t],
  );

  return {
    draft,
    isCreate,
    isDirty,
    isNameValid,
    areStepsValid,
    isValid,
    stepIssues,
    isInitializing: !!modeId && initializedModeId !== modeId,
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
    newRating,
    setNewRating,
    newCondition,
    setNewCondition,
    setName,
    moveStep,
    deleteStep,
    addStepToMode,
    resetSteps,
    confirmAddStep,
    save,
    stepLabels,
  };
}

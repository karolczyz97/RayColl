import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type {
  AtomicStep,
  CompoundParams,
  CompoundStep,
  ModeStep,
  StudyMode,
} from '@/types/models';
import { STUDY_MODE_NEW_ID } from '@/constants/routes';
import { MAX_VISIBLE_PAGE_COUNT } from '@/constants/pages';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { createSeedModes } from '@/store/seed/seedModes';
import { normalizeStudyMode } from '@/store/storeDataNormalization';
import { swapElements } from '@/utils/array';
import { deepEqual } from '@/utils/deepEqual';
import { uid } from '@/utils/id';
import { expandWithSource } from './compoundSteps';
import { createModeFromTemplate, type ModeTemplateId } from './modeTemplates';
import { hasBlockingStepIssue, validateModeStepSources } from './validateModeSteps';

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
    updatedAt: 0,
  };
}

function getSeedSteps(mode: StudyMode): ModeStep[] | null {
  if (!mode.isBuiltIn) return null;
  const sourceId = mode.builtInSourceId;
  if (!sourceId) return null;
  const seed = createSeedModes().find((seedMode) => seedMode.id === sourceId);
  return seed ? seed.steps.map(cloneStep) : null;
}


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
  const isCreate = modeId === STUDY_MODE_NEW_ID;
  const initializedModeIdRef = useRef<string | null>(null);
  const [draftState, setDraftState] = useState<DraftState>({
    initializedModeId: null,
    draft: null,
    original: null,
  });
  const { draft, original, initializedModeId } = draftState;

  const [stepDialogOpen, setStepDialogOpen] = useState(false);
  const [stepDialogKey, setStepDialogKey] = useState(0);
  const [compoundDialogOpen, setCompoundDialogOpen] = useState(false);
  const [compoundDialogKey, setCompoundDialogKey] = useState(0);
  const [editingCompoundIndex, setEditingCompoundIndex] = useState<number | null>(null);
  const [compoundDialogStep, setCompoundDialogStep] = useState<CompoundStep | null>(null);
  const [expertMode, setExpertMode] = useState(false);
  const [selectedTemplate, setSelectedTemplate] = useState<ModeTemplateId>('blank');

  useEffect(() => {
    if (!modeId || initializedModeIdRef.current === modeId) return;

    if (modeId === STUDY_MODE_NEW_ID) {
      const nextDraft = createDraftMode();
      initializedModeIdRef.current = modeId;
      // eslint-disable-next-line react-hooks/set-state-in-effect -- route draft initializes once when the route id becomes available
      setSelectedTemplate('blank');
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
    if (expertMode) {
      setStepDialogKey((key) => key + 1);
      setStepDialogOpen(true);
      return;
    }
    setEditingCompoundIndex(null);
    setCompoundDialogStep(null);
    setCompoundDialogKey((key) => key + 1);
    setCompoundDialogOpen(true);
  }, [expertMode]);

  const editStep = useCallback(
    (_mode: StudyMode, index: number) => {
      const step = draft?.steps[index];
      if (!step || step.type !== 'compound') return;
      setEditingCompoundIndex(index);
      setCompoundDialogStep(step);
      setCompoundDialogKey((key) => key + 1);
      setCompoundDialogOpen(true);
    },
    [draft],
  );

  const resetSteps = useCallback((_mode: StudyMode) => {
    setDraftState((current) => {
      if (!current.draft) return current;
      const seedSteps = getSeedSteps(current.draft);
      return seedSteps
        ? { ...current, draft: { ...current.draft, steps: seedSteps } }
        : current;
    });
  }, []);

  const applyTemplate = useCallback((templateId: ModeTemplateId) => {
    setSelectedTemplate(templateId);
    setDraftState((current) => {
      if (!current.draft) return current;
      return {
        ...current,
        draft: {
          ...current.draft,
          steps: createModeFromTemplate(templateId),
        },
      };
    });
  }, []);

  const addAtomicStep = useCallback((step: AtomicStep) => {
    setDraftState((current) => {
      if (!current.draft) return current;
      const stepWithId = { ...step, id: uid() };
      return { ...current, draft: { ...current.draft, steps: [...current.draft.steps, stepWithId] } };
    });
    setStepDialogOpen(false);
  }, []);

  const confirmCompoundStep = useCallback((params: CompoundParams) => {
    setDraftState((current) => {
      if (!current.draft) return current;

      if (editingCompoundIndex !== null) {
        const currentStep = current.draft.steps[editingCompoundIndex];
        if (!currentStep || currentStep.type !== 'compound') return current;
        const nextSteps = current.draft.steps.map((step, index) =>
          index === editingCompoundIndex
            ? { ...currentStep, params, version: 1 as const }
            : step,
        );
        return { ...current, draft: { ...current.draft, steps: nextSteps } };
      }

      const step: CompoundStep = { id: uid(), type: 'compound', version: 1, params };
      return {
        ...current,
        draft: { ...current.draft, steps: [...current.draft.steps, step] },
      };
    });
    setCompoundDialogOpen(false);
    setEditingCompoundIndex(null);
    setCompoundDialogStep(null);
  }, [editingCompoundIndex]);

  const isDirty = useMemo(() => {
    if (!draft) return false;
    if (isCreate) {
      return draft.name.trim().length > 0 || draft.steps.length > 0;
    }
    return original ? !deepEqual(draft, original) : false;
  }, [draft, isCreate, original]);

  const stepIssues = useMemo(
    () => (draft ? validateModeStepSources(expandWithSource(draft.steps), pageCount) : []),
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
    stepDialogKey,
    setStepDialogOpen,
    compoundDialogOpen,
    compoundDialogKey,
    setCompoundDialogOpen,
    compoundDialogStep,
    compoundDialogMode: editingCompoundIndex === null ? 'add' as const : 'edit' as const,
    expertMode,
    setExpertMode,
    selectedTemplate,
    applyTemplate,
    setName,
    moveStep,
    deleteStep,
    addStepToMode,
    editStep,
    resetSteps,
    addAtomicStep,
    confirmCompoundStep,
    save,
  };
}

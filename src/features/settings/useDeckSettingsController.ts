import { useEffect, useMemo, useState } from 'react';
import { useLocalSearchParams } from 'expo-router';
import { safeBack } from '../../utils/navigation';
import type { ModeStep, StudyMode } from '../../types/models';
import type { CardFilter } from '../../constants/cardFilters';
import { useFlashcardStore } from '../../hooks/useFlashcardStore';
import { useI18n } from '../../i18n';
import { POPULAR_LANGS } from '../../constants/languages';
import { uid } from '../../utils/id';
import { createSeedModes } from '../../store/seed/seedModes';
import { useResponsiveLayout } from '../../hooks/useResponsiveLayout';
import { stepSummary } from './studyModeUtils';

export function useDeckSettingsController() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();
  const { t } = useI18n();
  const store = useFlashcardStore();
  const responsiveLayout = useResponsiveLayout();
  const activeGroup = store.groups.find((group) => group.id === groupId) ?? null;
  const activeGroupId = activeGroup?.id ?? null;
  const activeGroupName = activeGroup?.name ?? '';
  const activeGroupPageNames = activeGroup?.pageNames;
  const pageNamesKey = activeGroupPageNames?.join('\u0000') ?? '';

  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deleteConfirmText, setDeleteConfirmText] = useState('');
  const [stepDialogOpen, setStepDialogOpen] = useState(false);
  const [newStepType, setNewStepType] = useState<string>('show_page');
  const [newPageIdx, setNewPageIdx] = useState(0);
  const [newMs, setNewMs] = useState(500);
  const [newThreshold, setNewThreshold] = useState(70);
  const [creatingMode, setCreatingMode] = useState(false);
  const [newModeName, setNewModeName] = useState('');
  const [customSteps, setCustomSteps] = useState<ModeStep[]>([]);
  const [editingModeId, setEditingModeId] = useState<string | null>(null);
  const [deckName, setDeckName] = useState('');
  const [colNames, setColNames] = useState<string[]>([]);

  useEffect(() => {
    if (!activeGroupId) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- local draft resets only when the active deck changes
    setDeckName(activeGroupName);
  }, [activeGroupId, activeGroupName]);

  useEffect(() => {
    if (!activeGroupId || !activeGroupPageNames) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- local draft resets only when the active deck changes
    setColNames(activeGroupPageNames);
  }, [activeGroupId, activeGroupPageNames, pageNamesKey]);

  const closeCreateModeDialog = () => {
    setCreatingMode(false);
    setEditingModeId(null);
    setCustomSteps([]);
    setNewModeName('');
  };

  const handleNameBlur = () => {
    if (!activeGroup) return;
    const trimmed = deckName.trim();
    if (trimmed && trimmed !== activeGroup.name) {
      store.updateGroup({ ...activeGroup, name: trimmed });
    }
  };

  const handleColBlur = (index: number) => {
    if (!activeGroup) return;
    const trimmed = colNames[index]?.trim();
    if (trimmed && trimmed !== activeGroup.pageNames[index]) {
      const nextNames = [...activeGroup.pageNames];
      nextNames[index] = trimmed;
      store.updateGroup({ ...activeGroup, pageNames: nextNames });
    }
  };

  const activeMode = store.studyModes.find((mode) => mode.id === activeGroup?.activeModeId) ?? null;
  const isDefaultMode = Boolean(activeMode?.isBuiltIn && activeMode.builtInSourceId);
  const hasCustomSteps = useMemo(() => {
    if (!activeMode?.isBuiltIn || !activeMode.builtInSourceId) return false;
    const seed = createSeedModes().find((mode) => mode.id === activeMode.builtInSourceId);
    if (!seed) return false;
    return JSON.stringify(seed.steps) !== JSON.stringify(activeMode.steps);
  }, [activeMode]);
  const pageCount = activeGroup?.activePageCount ?? 0;

  const adjustPageCount = (count: number) => {
    if (!activeGroup) return;
    store.setVisiblePageCount(activeGroup.id, count);
  };

  const updatePageLangValue = (index: number, value: string) => {
    if (!activeGroup) return;
    const nextLanguages = [...activeGroup.pageLanguages];
    nextLanguages[index] = value;
    store.updateGroup({ ...activeGroup, pageLanguages: nextLanguages });
  };

  const movePageSetting = (index: number, direction: -1 | 1) => {
    if (!activeGroup) return;
    const target = index + direction;
    if (target < 0 || target >= pageCount) return;

    const nextNames = [...activeGroup.pageNames];
    const nextLanguages = [...activeGroup.pageLanguages];
    [nextNames[index], nextNames[target]] = [nextNames[target], nextNames[index]];
    [nextLanguages[index], nextLanguages[target]] = [nextLanguages[target], nextLanguages[index]];

    const updatedCards = activeGroup.cards.map((card) => {
      const pages = [...card.pages];
      [pages[index], pages[target]] = [pages[target], pages[index]];
      return { ...card, pages };
    });

    store.updateGroup({
      ...activeGroup,
      pageNames: nextNames,
      pageLanguages: nextLanguages,
      cards: updatedCards,
    });
  };

  const moveStep = (mode: StudyMode, index: number, direction: -1 | 1) => {
    const target = index + direction;
    if (target < 0 || target >= mode.steps.length) return;
    const steps = [...mode.steps];
    [steps[index], steps[target]] = [steps[target], steps[index]];
    store.updateStudyMode({ ...mode, steps });
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
        step = { type: 'show_page', pageIndex: safePageIdx };
        break;
      case 'speak_page':
        step = { type: 'speak_page', pageIndex: safePageIdx, extraPauseMs: newMs };
        break;
      case 'dynamic_pause':
        step = { type: 'dynamic_pause', nextPageIndex: safePageIdx, extraPauseMs: newMs };
        break;
      case 'wait':
        step = { type: 'wait', ms: newMs };
        break;
      case 'listen_and_branch':
        step = { type: 'listen_and_branch', pageIndex: safePageIdx, successThreshold: newThreshold };
        break;
      case 'reveal_on_tap':
        step = { type: 'reveal_on_tap' };
        break;
      case 'rate':
        step = { type: 'rate' };
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
      setCustomSteps((steps) => [...steps, step]);
    }

    setStepDialogOpen(false);
    setEditingModeId(null);
  };

  const saveCustomMode = () => {
    if (!activeGroup || !newModeName.trim() || customSteps.length === 0) return;
    const mode: StudyMode = {
      id: uid(),
      name: newModeName.trim(),
      steps: customSteps,
      isBuiltIn: false,
    };
    store.addStudyMode(mode);
    store.updateGroup({ ...activeGroup, activeModeId: mode.id });
    closeCreateModeDialog();
  };

  const handleDeleteGroup = () => {
    if (activeGroup && deleteConfirmText === 'DELETE') {
      store.deleteGroup(activeGroup.id);
      setDeleteDialogOpen(false);
      safeBack();
    }
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
    activeGroup,
    activeMode,
    colNames,
    confirmAddStep,
    creatingMode,
    customSteps,
    deckName,
    deleteConfirmText,
    deleteDialogOpen,
    editingModeId,
    handleBack: safeBack,
    handleColBlur,
    handleDeleteGroup,
    handleNameBlur,
    isCompact: responsiveLayout.isCompact,
    isDefaultMode,
    hasCustomSteps,
    isExpanded: responsiveLayout.isExpanded,
    isLoading: store.isLoading,
    movePageSetting,
    moveStep,
    newModeName,
    newMs,
    newPageIdx,
    newStepType,
    newThreshold,
    onFilterChange: (filter: CardFilter) => {
      if (!activeGroup) return;
      store.updateGroup({ ...activeGroup, studyFilter: filter });
    },
    onModeChange: (modeId: string) => {
      if (!activeGroup) return;
      store.updateGroup({ ...activeGroup, activeModeId: modeId });
    },
    openCreateModeDialog: () => {
      setEditingModeId(null);
      setCreatingMode(true);
    },
    pageCount,
    popularLangs: POPULAR_LANGS,
    responsiveLayout,
    saveCustomMode,
    setColNames,
    setCreatingMode,
    setCustomSteps,
    setDeckName,
    setDeleteConfirmText,
    setDeleteDialogOpen,
    setEditingModeId,
    setNewModeName,
    setNewMs,
    setNewPageIdx,
    setNewStepType,
    setNewThreshold,
    setStepDialogOpen,
    stepDialogOpen,
    stepLabels,
    stepSummary,
    store,
    t,
    updatePageLangValue,
    adjustPageCount,
    closeCreateModeDialog,
    deleteStep,
    addStepToMode,
    resetMode,
  };
}

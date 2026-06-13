import { useEffect, useMemo, useState } from 'react';
import { useLocalSearchParams } from 'expo-router';
import { navigateUp } from '@/utils/navigation';
import type { ModeStep, StudyMode } from '@/types/models';
import type { CardFilter } from '@/constants/cardFilters';
import type { CardOrder } from '@/constants/cardOrder';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { useI18n } from '@/i18n';
import { POPULAR_LANGS } from '@/constants/languages';
import { uid } from '@/utils/id';
import { useResponsiveLayout } from '@/hooks/useResponsiveLayout';
import { useNavigationShell } from '@/contexts/NavigationShellContext';
import { isExpandedWindowSize } from '@/utils/windowSizeClass';
import { formatStepSummary, getModeCustomization } from './studyModeUtils';
import { reorderDeckPages } from './deckPageReorder';
import { useStepEditorController } from './useStepEditorController';

export function useDeckSettingsController() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();
  const { t } = useI18n();
  const store = useFlashcardStore();
  const responsiveLayout = useResponsiveLayout();
  const navigationShell = useNavigationShell();
  const activeGroup = store.groups.find((group) => group.id === groupId) ?? null;
  const activeGroupId = activeGroup?.id ?? null;
  const activeGroupName = activeGroup?.name ?? '';
  const activeGroupPageNames = activeGroup?.pageNames;
  const pageNamesKey = activeGroupPageNames?.join('\u0000') ?? '';

  const [archiveDialogOpen, setArchiveDialogOpen] = useState(false);
  const [creatingMode, setCreatingMode] = useState(false);
  const [newModeName, setNewModeName] = useState('');
  const [customSteps, setCustomSteps] = useState<ModeStep[]>([]);
  const [editingModeId, setEditingModeId] = useState<string | null>(null);
  const [deckName, setDeckName] = useState('');
  const [colNames, setColNames] = useState<string[]>([]);
  const [deckNameTouched, setDeckNameTouched] = useState(false);
  const [touchedPageNameIndexes, setTouchedPageNameIndexes] = useState<Set<number>>(
    () => new Set(),
  );

  useEffect(() => {
    if (!activeGroupId) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- local draft resets only when the active deck changes
    setDeckName(activeGroupName);
    setDeckNameTouched(false);
  }, [activeGroupId, activeGroupName]);

  useEffect(() => {
    if (!activeGroupId || !activeGroupPageNames) return;
    // eslint-disable-next-line react-hooks/set-state-in-effect -- local draft resets only when the active deck changes
    setColNames(activeGroupPageNames);
    setTouchedPageNameIndexes(new Set());
  }, [activeGroupId, activeGroupPageNames, pageNamesKey]);

  const closeCreateModeDialog = () => {
    setCreatingMode(false);
    setEditingModeId(null);
    setCustomSteps([]);
    setNewModeName('');
  };

  const handleNameBlur = () => {
    if (!activeGroup) return;
    setDeckNameTouched(true);
    const trimmed = deckName.trim();
    if (!trimmed) return;
    if (trimmed && trimmed !== activeGroup.name) {
      store.updateGroup({ ...activeGroup, name: trimmed });
      setDeckName(trimmed);
    }
  };

  const handleColBlur = (index: number) => {
    if (!activeGroup) return;
    setTouchedPageNameIndexes((prev) => {
      const next = new Set(prev);
      next.add(index);
      return next;
    });
    const trimmed = colNames[index]?.trim();
    if (!trimmed) return;
    if (trimmed && trimmed !== activeGroup.pageNames[index]) {
      const nextNames = [...activeGroup.pageNames];
      nextNames[index] = trimmed;
      store.updateGroup({ ...activeGroup, pageNames: nextNames });
      setColNames((prev) => {
        const next = [...prev];
        next[index] = trimmed;
        return next;
      });
    }
  };

  const activeMode = store.studyModes.find((mode) => mode.id === activeGroup?.activeModeId) ?? null;
  const { isDefaultMode, hasCustomSteps } = useMemo(
    () =>
      activeMode
        ? getModeCustomization(activeMode)
        : { isDefaultMode: false, hasCustomSteps: false },
    [activeMode],
  );
  const pageCount = activeGroup?.activePageCount ?? 0;
  const useTwoColumnLayout = isExpandedWindowSize(navigationShell.contentWidth);
  const deckNameError = deckNameTouched && !deckName.trim();
  const pageNameErrors = useMemo(
    () => colNames.map((name, index) => touchedPageNameIndexes.has(index) && !name.trim()),
    [colNames, touchedPageNameIndexes],
  );

  const stepEditor = useStepEditorController({
    pageCount,
    creatingMode,
    editingModeId,
    customSteps,
    setCustomSteps,
    setEditingModeId,
  });

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
    movePageInGroup(activeGroup, index, target);
  };

  const movePageSettingAll = (index: number, direction: -1 | 1) => {
    if (!activeGroup) return;
    const allCount = activeGroup.pageNames.length;
    const target = index + direction;
    if (target < 0 || target >= allCount) return;
    movePageInGroup(activeGroup, index, target);
  };

  const movePageInGroup = (group: typeof activeGroup, index: number, target: number) => {
    if (!group) return;
    store.updateGroup(reorderDeckPages(group, index, target));
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

  const handleArchiveGroup = async () => {
    if (!activeGroup) return;
    setArchiveDialogOpen(false);
    try {
      await store.archiveGroup(activeGroup.id);
      navigateUp();
    } catch {
      // Persist failed and the archive was rolled back; the error is surfaced via
      // store state. Stay on the screen so the user can retry instead of navigating
      // away from a deck that is still present.
    }
  };

  return {
    activeGroup,
    activeMode,
    colNames,
    confirmAddStep: stepEditor.confirmAddStep,
    creatingMode,
    customSteps,
    deckName,
    deckNameError,
    archiveDialogOpen,
    editingModeId,
    handleBack: navigateUp,
    handleColBlur,
    handleArchiveGroup,
    handleNameBlur,
    isCompact: responsiveLayout.isCompact,
    isDefaultMode,
    hasCustomSteps,
    isExpanded: useTwoColumnLayout,
    useTwoColumnLayout,
    isLoading: store.isLoading,
    movePageSetting,
    movePageSettingAll,
    moveStep: stepEditor.moveStep,
    newModeName,
    newMs: stepEditor.newMs,
    newPauseMultiplier: stepEditor.newPauseMultiplier,
    newPageIdx: stepEditor.newPageIdx,
    newStepType: stepEditor.newStepType,
    newThreshold: stepEditor.newThreshold,
    newCondition: stepEditor.newCondition,
    onFilterChange: (filter: CardFilter) => {
      if (!activeGroup) return;
      store.setStudyFilter(activeGroup.id, filter);
    },
    onCardOrderChange: (order: CardOrder) => {
      if (!activeGroup) return;
      store.setCardOrder(activeGroup.id, order);
    },
    onModeChange: (modeId: string) => {
      if (!activeGroup) return;
      store.setActiveStudyMode(activeGroup.id, modeId);
    },
    openCreateModeDialog: () => {
      setEditingModeId(null);
      setCreatingMode(true);
    },
    pageCount,
    pageNameErrors,
    popularLangs: POPULAR_LANGS,
    responsiveLayout,
    saveCustomMode,
    setColNames,
    setCreatingMode,
    setCustomSteps,
    setDeckName,
    setArchiveDialogOpen,
    setEditingModeId,
    setNewModeName,
    setNewMs: stepEditor.setNewMs,
    setNewPauseMultiplier: stepEditor.setNewPauseMultiplier,
    setNewPageIdx: stepEditor.setNewPageIdx,
    setNewStepType: stepEditor.setNewStepType,
    setNewThreshold: stepEditor.setNewThreshold,
    setNewCondition: stepEditor.setNewCondition,
    setStepDialogOpen: stepEditor.setStepDialogOpen,
    stepDialogOpen: stepEditor.stepDialogOpen,
    stepLabels: stepEditor.stepLabels,
    formatStepSummary,
    store,
    t,
    updatePageLangValue,
    adjustPageCount,
    closeCreateModeDialog,
    deleteStep: stepEditor.deleteStep,
    addStepToMode: stepEditor.addStepToMode,
    resetMode: stepEditor.resetMode,
    renameMode: stepEditor.renameMode,
  };
}

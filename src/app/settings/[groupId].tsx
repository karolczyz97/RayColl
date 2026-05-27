import React, { useState } from 'react';
import { View, StyleSheet, ScrollView } from 'react-native';
import { IconButton, useTheme, Portal, ActivityIndicator } from 'react-native-paper';
import { useLocalSearchParams, router } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import Animated, { FadeInDown } from 'react-native-reanimated';
import { useFlashcardStore } from '../../hooks/useFlashcardStore';
import { useI18n } from '../../i18n';
import type { TranslationFn } from '../../i18n';
import type { ModeStep, StudyMode, FlashcardGroup } from '../../types/models';
import { PageHeader } from '../../components/PageHeader';
import { GroupNotFound } from '../../components/GroupNotFound';
import { CardFilter } from '../../constants/cardFilters';
import { getVisiblePageNames, getVisiblePageLanguages } from '../../store/selectors/pages';
import { POPULAR_LANGS } from '../../constants/languages';
import { uid } from '../../utils/id';

import { DeckNameSection } from '../../components/settings/DeckNameSection';
import { PagesConfigSection } from '../../components/settings/PagesConfigSection';
import { StudyScopeSection } from '../../components/settings/StudyScopeSection';
import { StudyModeSelector } from '../../components/settings/StudyModeSelector';
import { StudyModeStepsEditor } from '../../components/settings/StudyModeStepsEditor';
import { CreateStudyModeSection } from '../../components/settings/CreateStudyModeSection';
import { DeleteDeckDialog } from '../../components/settings/DeleteDeckDialog';
import { AddStepDialog } from '../../components/settings/AddStepDialog';
import { AppCard } from '../../components/AppCard';
import { TOKENS } from '../../theme/tokens';
import { useResponsiveLayout } from '../../hooks/useResponsiveLayout';

const DEFAULT_MODE_IDS = ['classic', 'listen-speak'];

function stepSummary(step: ModeStep, t: TranslationFn): string {
  switch (step.type) {
    case 'show_page':
      return t('step.show_page', { index: step.pageIndex + 1 });
    case 'speak_page':
      return t('step.speak_page', { index: step.pageIndex + 1, pause: step.extraPauseMs });
    case 'dynamic_pause':
      return t('step.dynamic_pause', { index: step.nextPageIndex + 1, pause: step.extraPauseMs });
    case 'wait':
      return t('step.wait', { ms: step.ms });
    case 'listen_and_branch':
      return t('step.listen_and_branch', {
        index: step.pageIndex + 1,
        threshold: step.successThreshold,
      });
  }
}

export default function SettingsPage() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();
  const { t } = useI18n();
  const theme = useTheme();
  const store = useFlashcardStore();
  const { isCompact, isExpanded, contentMaxWidth } = useResponsiveLayout();

  const group = store.groups.find((g) => g.id === groupId);

  const activeGroup = group;

  // Dialog visibility
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deleteConfirmText, setDeleteConfirmText] = useState('');
  const [stepDialogOpen, setStepDialogOpen] = useState(false);

  // New step creation state
  const [newStepType, setNewStepType] = useState<string>('show_page');
  const [newPageIdx, setNewPageIdx] = useState(0);
  const [newMs, setNewMs] = useState(500);
  const [newThreshold, setNewThreshold] = useState(70);

  // Custom mode creation state
  const [creatingMode, setCreatingMode] = useState(false);
  const [newModeName, setNewModeName] = useState('');
  const [customSteps, setCustomSteps] = useState<ModeStep[]>([]);
  const [editingModeId, setEditingModeId] = useState<string | null>(null);

  const openCreateModeDialog = () => {
    setEditingModeId(null);
    setCreatingMode(true);
  };

  const closeCreateModeDialog = () => {
    setCreatingMode(false);
    setEditingModeId(null);
    setCustomSteps([]);
    setNewModeName('');
  };

  const [prevGroupName, setPrevGroupName] = useState(activeGroup?.name || '');
  const [deckName, setDeckName] = useState(activeGroup?.name || '');
  if (activeGroup && activeGroup.name !== prevGroupName) {
    setPrevGroupName(activeGroup.name);
    setDeckName(activeGroup.name);
  }

  const [prevPageNames, setPrevPageNames] = useState(activeGroup?.pageNames || []);
  const [colNames, setColNames] = useState(activeGroup?.pageNames || []);
  if (activeGroup && activeGroup.pageNames !== prevPageNames) {
    setPrevPageNames(activeGroup.pageNames);
    setColNames(activeGroup.pageNames);
  }

  const handleNameBlur = () => {
    if (!activeGroup) return;
    const trimmed = deckName.trim();
    if (trimmed && trimmed !== activeGroup.name) {
      store.updateGroup({ ...activeGroup, name: trimmed } as FlashcardGroup);
    }
  };

  const handleColBlur = (i: number) => {
    if (!activeGroup) return;
    const trimmed = colNames[i]?.trim();
    if (trimmed && trimmed !== activeGroup.pageNames[i]) {
      const nextNames = [...activeGroup.pageNames];
      nextNames[i] = trimmed;
      store.updateGroup({ ...activeGroup, pageNames: nextNames } as FlashcardGroup);
    }
  };

  if (store.isLoading) {
    return (
      <View style={[styles.centerContainer, { backgroundColor: theme.colors.background }]}>
        <ActivityIndicator size="large" color={theme.colors.primary} />
      </View>
    );
  }

  if (!activeGroup) {
    return <GroupNotFound onBack={() => router.back()} />;
  }

  const activeMode = store.studyModes.find((m) => m.id === activeGroup.activeModeId);
  const isDefaultMode = DEFAULT_MODE_IDS.includes(activeMode?.id || '');

  const pageCount = activeGroup.activePageCount ?? activeGroup.pageNames.length;
  const adjustPageCount = (count: number) => {
    store.setVisiblePageCount(activeGroup.id, count);
  };

  const updatePageLangValue = (i: number, v: string) => {
    const langs = [...activeGroup.pageLanguages];
    langs[i] = v;
    store.updateGroup({ ...activeGroup, pageLanguages: langs });
  };

  const movePageSetting = (i: number, dir: -1 | 1) => {
    const j = i + dir;
    if (j < 0 || j >= pageCount) return;
    const nn = [...activeGroup.pageNames];
    const nl = [...activeGroup.pageLanguages];
    [nn[i], nn[j]] = [nn[j], nn[i]];
    [nl[i], nl[j]] = [nl[j], nl[i]];

    const updatedCards = activeGroup.cards.map((card) => {
      const pages = [...card.pages];
      [pages[i], pages[j]] = [pages[j], pages[i]];
      return { ...card, pages };
    });
    store.updateGroup({ ...activeGroup, pageNames: nn, pageLanguages: nl, cards: updatedCards });
  };

  const moveStep = (mode: StudyMode, i: number, dir: -1 | 1) => {
    const j = i + dir;
    if (j < 0 || j >= mode.steps.length) return;
    const steps = [...mode.steps];
    [steps[i], steps[j]] = [steps[j], steps[i]];
    store.updateStudyMode({ ...mode, steps });
  };

  const deleteStep = (mode: StudyMode, i: number) => {
    const steps = mode.steps.filter((_, j) => j !== i);
    store.updateStudyMode({ ...mode, steps });
  };

  const addStepToMode = (mode: StudyMode) => {
    setEditingModeId(mode.id);
    setStepDialogOpen(true);
  };

  const confirmAddStep = () => {
    let step: ModeStep;
    switch (newStepType) {
      case 'show_page':
        step = { type: 'show_page', pageIndex: newPageIdx };
        break;
      case 'speak_page':
        step = { type: 'speak_page', pageIndex: newPageIdx, extraPauseMs: newMs };
        break;
      case 'dynamic_pause':
        step = { type: 'dynamic_pause', nextPageIndex: newPageIdx, extraPauseMs: newMs };
        break;
      case 'wait':
        step = { type: 'wait', ms: newMs };
        break;
      case 'listen_and_branch':
        step = { type: 'listen_and_branch', pageIndex: newPageIdx, successThreshold: newThreshold };
        break;
      default:
        return;
    }

    if (editingModeId && !creatingMode) {
      const mode = store.studyModes.find((m) => m.id === editingModeId);
      if (mode) {
        store.updateStudyMode({ ...mode, steps: [...mode.steps, step] });
      }
    } else {
      setCustomSteps((s) => [...s, step]);
    }
    setStepDialogOpen(false);
    setEditingModeId(null);
  };

  const saveCustomMode = () => {
    if (!newModeName.trim() || customSteps.length === 0) return;
    const mode: StudyMode = { id: uid(), name: newModeName.trim(), steps: customSteps };
    store.addStudyMode(mode);
    store.updateGroup({ ...activeGroup, activeModeId: mode.id });
    setCreatingMode(false);
    setCustomSteps([]);
    setNewModeName('');
  };

  const handleDeleteGroup = () => {
    if (deleteConfirmText === 'DELETE') {
      store.deleteGroup(activeGroup.id);
      setDeleteDialogOpen(false);
      router.back();
    }
  };

  const onModeChange = (modeId: string) => {
    store.updateGroup({ ...activeGroup, activeModeId: modeId });
  };

  const onFilterChange = (filter: CardFilter) => {
    store.updateGroup({ ...activeGroup, studyFilter: filter });
  };

  const STEP_LABELS: Record<string, string> = {
    show_page: t('step.type.show_page'),
    speak_page: t('step.type.speak_page'),
    dynamic_pause: t('step.type.dynamic_pause'),
    wait: t('step.type.wait'),
    listen_and_branch: t('step.type.listen_and_branch'),
  };

  const leftColumnContent = (
    <>
      {/* Rename Field Card */}
      <Animated.View entering={FadeInDown.springify().delay(0)}>
        <AppCard style={styles.card} mode="outlined">
          <AppCard.Content>
            <DeckNameSection
              deckName={deckName}
              onChangeText={setDeckName}
              onBlur={handleNameBlur}
              t={t}
            />
          </AppCard.Content>
        </AppCard>
      </Animated.View>

      {/* Study Scope Selection Card */}
      <Animated.View entering={FadeInDown.springify().delay(80)}>
        <AppCard style={styles.card} mode="outlined">
          <AppCard.Content>
            <StudyScopeSection
              studyFilter={activeGroup.studyFilter || 'new+review'}
              onFilterChange={onFilterChange}
              t={t}
            />
          </AppCard.Content>
        </AppCard>
      </Animated.View>

      {/* Pages configuration Card */}
      <Animated.View entering={FadeInDown.springify().delay(160)}>
        <AppCard style={styles.card} mode="outlined">
          <AppCard.Content>
            <PagesConfigSection
              pageCount={pageCount}
              visiblePageNames={getVisiblePageNames({ ...activeGroup, pageNames: colNames })}
              visiblePageLanguages={getVisiblePageLanguages(activeGroup)}
              adjustPageCount={adjustPageCount}
              movePageSetting={movePageSetting}
              setColNames={setColNames}
              handleColBlur={handleColBlur}
              updatePageLangValue={updatePageLangValue}
              t={t}
              popularLangs={POPULAR_LANGS}
            />
          </AppCard.Content>
        </AppCard>
      </Animated.View>
    </>
  );

  const rightColumnContent = (
    <>
      {/* Study Mode Selection Card */}
      <Animated.View entering={FadeInDown.springify().delay(240)}>
        <AppCard style={styles.card} mode="outlined">
          <AppCard.Content>
            <StudyModeSelector
              activeModeId={activeGroup.activeModeId}
              onModeChange={onModeChange}
              studyModes={store.studyModes}
              t={t}
              onCreateMode={openCreateModeDialog}
            />
          </AppCard.Content>
        </AppCard>
      </Animated.View>

      {/* Active Mode Steps */}
      {activeMode && (
        <Animated.View entering={FadeInDown.springify().delay(320)}>
          <StudyModeStepsEditor
            activeMode={activeMode}
            isDefaultMode={isDefaultMode}
            moveStep={moveStep}
            deleteStep={deleteStep}
            addStepToMode={addStepToMode}
            t={t}
            stepSummary={stepSummary}
          />
        </Animated.View>
      )}
    </>
  );

  const singleColumnContent = (
    <View style={[styles.singleColumn, !isCompact && { maxWidth: 600 }]}>
      {/* Rename Field Card */}
      <Animated.View entering={FadeInDown.springify().delay(0)}>
        <AppCard style={styles.card} mode="outlined">
          <AppCard.Content>
            <DeckNameSection
              deckName={deckName}
              onChangeText={setDeckName}
              onBlur={handleNameBlur}
              t={t}
            />
          </AppCard.Content>
        </AppCard>
      </Animated.View>

      {/* Pages configuration Card */}
      <Animated.View entering={FadeInDown.springify().delay(80)}>
        <AppCard style={styles.card} mode="outlined">
          <AppCard.Content>
            <PagesConfigSection
              pageCount={pageCount}
              visiblePageNames={getVisiblePageNames({ ...activeGroup, pageNames: colNames })}
              visiblePageLanguages={getVisiblePageLanguages(activeGroup)}
              adjustPageCount={adjustPageCount}
              movePageSetting={movePageSetting}
              setColNames={setColNames}
              handleColBlur={handleColBlur}
              updatePageLangValue={updatePageLangValue}
              t={t}
              popularLangs={POPULAR_LANGS}
            />
          </AppCard.Content>
        </AppCard>
      </Animated.View>

      {/* Study Scope Selection Card */}
      <Animated.View entering={FadeInDown.springify().delay(160)}>
        <AppCard style={styles.card} mode="outlined">
          <AppCard.Content>
            <StudyScopeSection
              studyFilter={activeGroup.studyFilter || 'new+review'}
              onFilterChange={onFilterChange}
              t={t}
            />
          </AppCard.Content>
        </AppCard>
      </Animated.View>

      {/* Study Mode Selection Card */}
      <Animated.View entering={FadeInDown.springify().delay(240)}>
        <AppCard style={styles.card} mode="outlined">
          <AppCard.Content>
            <StudyModeSelector
              activeModeId={activeGroup.activeModeId}
              onModeChange={onModeChange}
              studyModes={store.studyModes}
              t={t}
              onCreateMode={openCreateModeDialog}
            />
          </AppCard.Content>
        </AppCard>
      </Animated.View>

      {/* Active Mode Steps */}
      {activeMode && (
        <Animated.View entering={FadeInDown.springify().delay(320)}>
          <StudyModeStepsEditor
            activeMode={activeMode}
            isDefaultMode={isDefaultMode}
            moveStep={moveStep}
            deleteStep={deleteStep}
            addStepToMode={addStepToMode}
            t={t}
            stepSummary={stepSummary}
          />
        </Animated.View>
      )}
    </View>
  );

  return (
    <SafeAreaView
      style={[styles.root, { backgroundColor: theme.colors.background }]}
      edges={['top', 'left', 'right']}
    >
      <PageHeader
        title={t('settings.title', { name: activeGroup.name })}
        onBack={() => router.back()}
        action={
          <IconButton
            icon="delete-outline"
            iconColor={theme.colors.error}
            onPress={() => setDeleteDialogOpen(true)}
            accessibilityLabel="Delete deck button"
          />
        }
      />

      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={[styles.mainContainer, { maxWidth: contentMaxWidth }]}>
          {isExpanded ? (
            <View style={styles.row}>
              <View style={styles.column}>{leftColumnContent}</View>
              <View style={styles.column}>{rightColumnContent}</View>
            </View>
          ) : (
            singleColumnContent
          )}
        </View>
      </ScrollView>

      {/* Portal Dialogs */}
      <Portal>
        <CreateStudyModeSection
          visible={creatingMode}
          onDismiss={closeCreateModeDialog}
          newModeName={newModeName}
          setNewModeName={setNewModeName}
          customSteps={customSteps}
          setCustomSteps={setCustomSteps}
          saveCustomMode={saveCustomMode}
          setStepDialogOpen={setStepDialogOpen}
          setEditingModeId={setEditingModeId}
          t={t}
          stepSummary={stepSummary}
        />

        <AddStepDialog
          visible={stepDialogOpen}
          onDismiss={() => setStepDialogOpen(false)}
          newStepType={newStepType}
          setNewStepType={setNewStepType}
          newPageIdx={newPageIdx}
          setNewPageIdx={setNewPageIdx}
          newMs={newMs}
          setNewMs={setNewMs}
          newThreshold={newThreshold}
          setNewThreshold={setNewThreshold}
          confirmAddStep={confirmAddStep}
          t={t}
          stepLabels={STEP_LABELS}
        />

        <DeleteDeckDialog
          visible={deleteDialogOpen}
          onDismiss={() => setDeleteDialogOpen(false)}
          deckName={activeGroup.name}
          deleteConfirmText={deleteConfirmText}
          setDeleteConfirmText={setDeleteConfirmText}
          onDelete={handleDeleteGroup}
          t={t}
        />
      </Portal>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    paddingHorizontal: TOKENS.spacing.xxs,
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  scrollContent: {
    paddingBottom: TOKENS.spacing.xxl * 2,
    gap: TOKENS.spacing.lg,
  },
  mainContainer: {
    width: '100%',
    alignSelf: 'center',
    gap: TOKENS.spacing.lg,
    padding: TOKENS.spacing.xxs,
  },
  row: {
    flexDirection: 'row',
    gap: TOKENS.spacing.xl,
    width: '100%',
    alignItems: 'flex-start',
  },
  column: {
    flex: 1,
    gap: TOKENS.spacing.lg,
  },
  singleColumn: {
    width: '100%',
    alignSelf: 'center',
    gap: TOKENS.spacing.lg,
  },
  card: {
    borderRadius: TOKENS.radius.xl,
  },
});

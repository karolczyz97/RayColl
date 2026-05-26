import React, { useState, useRef } from 'react';
import { View, StyleSheet, ScrollView } from 'react-native';
import { Text, TextInput, Button, IconButton, useTheme, Divider, Menu, Portal, Dialog, List, ActivityIndicator, Card } from 'react-native-paper';
import { useLocalSearchParams, router } from 'expo-router';
import ReanimatedView, { FadeInDown } from 'react-native-reanimated';
import { useFlashcardStore } from '../../hooks/useFlashcardStore';
import { useI18n } from '../../i18n';
import type { ModeStep, StudyMode, FlashcardGroup } from '../../types/models';
import { PageHeader } from '../../components/PageHeader';
import { GroupNotFound } from '../../components/GroupNotFound';

function uid(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

const POPULAR_LANGS = [
  { code: 'pl-PL', label: 'Polski' }, { code: 'en-US', label: 'Angielski' },
  { code: 'es-ES', label: 'Hiszpański' }, { code: 'de-DE', label: 'Niemiecki' },
  { code: 'fr-FR', label: 'Francuski' }, { code: 'it-IT', label: 'Włoski' },
  { code: 'pt-PT', label: 'Portugalski' }, { code: 'ru-RU', label: 'Rosyjski' },
  { code: 'ja-JP', label: 'Japoński' }, { code: 'zh-CN', label: 'Chiński' },
];

const DEFAULT_MODE_IDS = ['classic', 'listen-speak'];

function stepSummary(step: ModeStep, t: (key: string, replacements?: any) => string): string {
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
      return t('step.listen_and_branch', { index: step.pageIndex + 1, threshold: step.successThreshold });
  }
}

export default function SettingsPage() {
  const { groupId } = useLocalSearchParams<{ groupId: string }>();
  const { t } = useI18n();
  const theme = useTheme();
  const store = useFlashcardStore();

  const group = store.groups.find((g) => g.id === groupId);

  // Cache last valid group
  const lastValidGroupRef = useRef<typeof group>(undefined);
  if (group) {
    lastValidGroupRef.current = group;
  }
  const activeGroup = group || lastValidGroupRef.current;

  // Dropdown/Dialog visibility
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deleteConfirmText, setDeleteConfirmText] = useState('');
  const [stepDialogOpen, setStepDialogOpen] = useState(false);
  const [modeMenuVisible, setModeMenuVisible] = useState(false);
  const [scopeMenuVisible, setScopeMenuVisible] = useState(false);
  const [langMenuIndex, setLangMenuIndex] = useState<number | null>(null);

  // New step creation state
  const [newStepType, setNewStepType] = useState<string>('show_page');
  const [newStepTypeVisible, setNewStepTypeVisible] = useState(false);
  const [newPageIdx, setNewPageIdx] = useState(0);
  const [newMs, setNewMs] = useState(500);
  const [newThreshold, setNewThreshold] = useState(70);

  // Custom mode creation state
  const [creatingMode, setCreatingMode] = useState(false);
  const [newModeName, setNewModeName] = useState('');
  const [customSteps, setCustomSteps] = useState<ModeStep[]>([]);
  const [editingModeId, setEditingModeId] = useState<string | null>(null);

  const removedNamesRef = useRef<string[]>([]);
  const removedLangsRef = useRef<string[]>([]);

  const [deckName, setDeckName] = useState(activeGroup?.name || '');
  const [colNames, setColNames] = useState(activeGroup?.pageNames || []);

  React.useEffect(() => {
    if (activeGroup) {
      setDeckName(activeGroup.name);
    }
  }, [activeGroup?.name]);

  React.useEffect(() => {
    if (activeGroup) {
      setColNames(activeGroup.pageNames);
    }
  }, [activeGroup?.pageNames]);

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

  const getModeName = (m: StudyMode) => {
    const key = `mode.${m.id}.name`;
    const translated = t(key);
    return translated === key ? m.name : translated;
  };

  const handleRename = (newName: string) => {
    store.updateGroup({ ...activeGroup, name: newName });
  };

  const pageCount = activeGroup.pageNames.length;
  const adjustPageCount = (count: number) => {
    if (count < pageCount) {
      removedNamesRef.current = activeGroup.pageNames.slice(count);
      removedLangsRef.current = activeGroup.pageLanguages.slice(count);
    }
    const names = [...activeGroup.pageNames];
    const langs = [...activeGroup.pageLanguages];
    while (names.length < count) {
      const restoreIdx = names.length - pageCount;
      if (restoreIdx >= 0 && restoreIdx < removedNamesRef.current.length) {
        names.push(removedNamesRef.current[restoreIdx]);
        langs.push(removedLangsRef.current[restoreIdx] || 'en-US');
      } else {
        names.push(t('import.page_label', { index: names.length + 1 }));
        langs.push('en-US');
      }
    }
    store.updateGroup({
      ...activeGroup,
      pageNames: names.slice(0, count),
      pageLanguages: langs.slice(0, count),
    });
  };

  const updatePageNameValue = (i: number, v: string) => {
    const names = [...activeGroup.pageNames];
    names[i] = v;
    store.updateGroup({ ...activeGroup, pageNames: names });
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
    store.deleteStudyMode(mode.id);
    store.addStudyMode({ ...mode, steps });
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
        store.deleteStudyMode(mode.id);
        store.addStudyMode({ ...mode, steps: [...mode.steps, step] });
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

  const STEP_LABELS: Record<string, string> = {
    show_page: t('step.type.show_page'),
    speak_page: t('step.type.speak_page'),
    dynamic_pause: t('step.type.dynamic_pause'),
    wait: t('step.type.wait'),
    listen_and_branch: t('step.type.listen_and_branch'),
  };

  return (
    <View style={[styles.root, { backgroundColor: theme.colors.background }]}>
      <PageHeader
        title={t('settings.title', { name: activeGroup.name })}
        onBack={() => router.back()}
        action={
          <IconButton icon="delete-outline" iconColor={theme.colors.error} onPress={() => setDeleteDialogOpen(true)} />
        }
      />

      <ScrollView contentContainerStyle={styles.scrollContent}>
        {/* Rename Field */}
        <TextInput
          mode="outlined"
          label={t('settings.rename_label')}
          value={deckName}
          onChangeText={setDeckName}
          onBlur={handleNameBlur}
          style={styles.input}
          outlineStyle={{ borderRadius: 12 }}
        />

        <Divider style={styles.divider} />

        {/* Pages configuration */}
        <Text variant="titleMedium" style={styles.sectionTitle}>
          {t('settings.pages_config')}
        </Text>
        <View style={styles.counterRow}>
          <Text>{t('import.pages_count')}</Text>
          <View style={styles.counterButtons}>
            <IconButton
              icon="minus-box"
              size={28}
              onPress={() => adjustPageCount(pageCount - 1)}
              disabled={pageCount <= 2}
            />
            <Text style={styles.counterText}>{pageCount}</Text>
            <IconButton
              icon="plus-box"
              size={28}
              onPress={() => adjustPageCount(pageCount + 1)}
              disabled={pageCount >= 5}
            />
          </View>
        </View>

        {colNames.map((pn, i) => (
          <View key={i} style={styles.columnRow}>
            <View style={styles.sortButtons}>
              <IconButton
                icon="arrow-up"
                size={16}
                style={styles.sortBtn}
                onPress={() => movePageSetting(i, -1)}
                disabled={i === 0}
              />
              <IconButton
                icon="arrow-down"
                size={16}
                style={styles.sortBtn}
                onPress={() => movePageSetting(i, 1)}
                disabled={i === pageCount - 1}
              />
            </View>
            <TextInput
              mode="outlined"
              label={t('import.page_label', { index: i + 1 })}
              value={pn}
              onChangeText={(v) => {
                const next = [...colNames];
                next[i] = v;
                setColNames(next);
              }}
              onBlur={() => handleColBlur(i)}
              style={styles.pageNameInput}
              outlineStyle={{ borderRadius: 12 }}
            />
            <Menu
              visible={langMenuIndex === i}
              onDismiss={() => setLangMenuIndex(null)}
              anchor={
                <Button
                  mode="outlined"
                  compact
                  style={styles.langBtn}
                  onPress={() => setLangMenuIndex(i)}
                >
                  {activeGroup.pageLanguages[i] ? t(`lang.${activeGroup.pageLanguages[i]}`) : t('import.lang_label')}
                </Button>
              }
            >
              {POPULAR_LANGS.map((l) => (
                <Menu.Item
                  key={l.code}
                  onPress={() => {
                    updatePageLangValue(i, l.code);
                    setLangMenuIndex(null);
                  }}
                  title={t(`lang.${l.code}`)}
                />
              ))}
            </Menu>
          </View>
        ))}

        <Divider style={styles.divider} />

        {/* Study Mode Selection */}
        <Text variant="titleMedium" style={styles.sectionTitle}>
          {t('settings.active_mode')}
        </Text>
        <Menu
          visible={modeMenuVisible}
          onDismiss={() => setModeMenuVisible(false)}
          anchor={
            <Button mode="outlined" onPress={() => setModeMenuVisible(true)} style={styles.dropdownAnchor}>
              {activeMode ? getModeName(activeMode) : t('settings.modes_title')}
            </Button>
          }
        >
          {store.studyModes.map((m) => (
            <Menu.Item
              key={m.id}
              onPress={() => {
                store.updateGroup({ ...activeGroup, activeModeId: m.id });
                setModeMenuVisible(false);
              }}
              title={getModeName(m)}
            />
          ))}
        </Menu>

        {/* Study Scope Selection */}
        <Text variant="titleMedium" style={styles.sectionTitle}>
          {t('settings.study_scope')}
        </Text>
        <Menu
          visible={scopeMenuVisible}
          onDismiss={() => setScopeMenuVisible(false)}
          anchor={
            <Button mode="outlined" onPress={() => setScopeMenuVisible(true)} style={styles.dropdownAnchor}>
              {t(`filter.${activeGroup.studyFilter || 'new_review'}`)}
            </Button>
          }
        >
          <Menu.Item
            onPress={() => {
              store.updateGroup({ ...activeGroup, studyFilter: 'new+review' });
              setScopeMenuVisible(false);
            }}
            title={t('filter.new_review')}
          />
          <Menu.Item
            onPress={() => {
              store.updateGroup({ ...activeGroup, studyFilter: 'new' });
              setScopeMenuVisible(false);
            }}
            title={t('filter.new')}
          />
          <Menu.Item
            onPress={() => {
              store.updateGroup({ ...activeGroup, studyFilter: 'review' });
              setScopeMenuVisible(false);
            }}
            title={t('filter.review')}
          />
          <Menu.Item
            onPress={() => {
              store.updateGroup({ ...activeGroup, studyFilter: 'all' });
              setScopeMenuVisible(false);
            }}
            title={t('filter.all')}
          />
        </Menu>

        {/* Active Mode Steps */}
        {activeMode && (
          <View style={styles.stepsSection}>
            <Text variant="titleSmall" style={{ fontWeight: 'bold', marginBottom: 8 }}>
              {t('settings.mode_steps', { name: getModeName(activeMode) })}{' '}
              {isDefaultMode && `(${t('settings.default_mode_notice')})`}
            </Text>
            <Card mode="outlined" style={styles.stepsCard}>
              <Card.Content style={{ padding: 0 }}>
                {activeMode.steps.map((step, i) => (
                  <View key={i}>
                    {i > 0 && <Divider />}
                    <List.Item
                      title={`${i + 1}. ${stepSummary(step, t)}`}
                      right={() =>
                        !isDefaultMode ? (
                          <View style={styles.stepControls}>
                            <IconButton
                              icon="arrow-up"
                              size={16}
                              style={styles.stepControlBtn}
                              onPress={() => moveStep(activeMode, i, -1)}
                              disabled={i === 0}
                            />
                            <IconButton
                              icon="arrow-down"
                              size={16}
                              style={styles.stepControlBtn}
                              onPress={() => moveStep(activeMode, i, 1)}
                              disabled={i === activeMode.steps.length - 1}
                            />
                            <IconButton
                              icon="delete"
                              size={16}
                              style={styles.stepControlBtn}
                              iconColor={theme.colors.error}
                              onPress={() => {
                                const steps = activeMode.steps.filter((_, j) => j !== i);
                                store.deleteStudyMode(activeMode.id);
                                store.addStudyMode({ ...activeMode, steps });
                              }}
                            />
                          </View>
                        ) : undefined
                      }
                    />
                  </View>
                ))}
              </Card.Content>
            </Card>
            {!isDefaultMode && (
              <Button icon="plus" mode="text" onPress={() => addStepToMode(activeMode)} style={{ alignSelf: 'flex-start' }}>
                {t('settings.add_step_btn')}
              </Button>
            )}
          </View>
        )}

        <Button mode="outlined" icon="plus" onPress={() => setCreatingMode(true)} style={styles.createModeBtn}>
          {t('settings.create_mode_btn')}
        </Button>

        {creatingMode && (
          <View style={[styles.createModeContainer, { backgroundColor: theme.colors.surfaceVariant }]}>
            <TextInput
              mode="outlined"
              label={t('settings.new_mode_name')}
              value={newModeName}
              onChangeText={setNewModeName}
              style={{ marginBottom: 12, height: 44 }}
              outlineStyle={{ borderRadius: 12 }}
            />
            {customSteps.map((step, i) => (
              <View key={i} style={styles.customStepRow}>
                <Text style={{ flex: 1 }}>{`${i + 1}. ${stepSummary(step, t)}`}</Text>
                <IconButton
                  icon="arrow-up"
                  size={16}
                  onPress={() => {
                    const s = [...customSteps];
                    [s[i], s[Math.max(0, i - 1)]] = [s[Math.max(0, i - 1)], s[i]];
                    setCustomSteps(s);
                  }}
                  disabled={i === 0}
                />
                <IconButton
                  icon="arrow-down"
                  size={16}
                  onPress={() => {
                    const s = [...customSteps];
                    const j = Math.min(s.length - 1, i + 1);
                    [s[i], s[j]] = [s[j], s[i]];
                    setCustomSteps(s);
                  }}
                  disabled={i === customSteps.length - 1}
                />
                <IconButton
                  icon="delete"
                  size={16}
                  iconColor={theme.colors.error}
                  onPress={() => setCustomSteps((s) => s.filter((_, j) => j !== i))}
                />
              </View>
            ))}
            <View style={styles.createModeActions}>
              <Button
                icon="plus"
                onPress={() => {
                  setEditingModeId(null);
                  setStepDialogOpen(true);
                }}
              >
                {t('settings.add_step_btn')}
              </Button>
              <Button
                mode="contained"
                onPress={saveCustomMode}
                disabled={!newModeName.trim() || customSteps.length === 0}
              >
                {t('settings.save_mode_btn')}
              </Button>
            </View>
          </View>
        )}
      </ScrollView>

      {/* Portal Dialogs */}
      <Portal>
        {/* Step Addition Dialog */}
        <Dialog visible={stepDialogOpen} onDismiss={() => setStepDialogOpen(false)}>
          <Dialog.Title>{t('settings.dialog.add_step.title')}</Dialog.Title>
          <Dialog.Content style={{ gap: 12 }}>
            <Menu
              visible={newStepTypeVisible}
              onDismiss={() => setNewStepTypeVisible(false)}
              anchor={
                <Button mode="outlined" onPress={() => setNewStepTypeVisible(true)}>
                  {`${t('settings.dialog.add_step.type')}: ${STEP_LABELS[newStepType]}`}
                </Button>
              }
            >
              {Object.entries(STEP_LABELS).map(([k, v]) => (
                <Menu.Item
                  key={k}
                  onPress={() => {
                    setNewStepType(k);
                    setNewStepTypeVisible(false);
                  }}
                  title={v}
                />
              ))}
            </Menu>

            {newStepType !== 'wait' && (
              <TextInput
                mode="outlined"
                label={t('settings.dialog.add_step.page_idx')}
                keyboardType="numeric"
                value={String(newPageIdx)}
                onChangeText={(v) => setNewPageIdx(Number(v) || 0)}
                style={{ height: 44 }}
                outlineStyle={{ borderRadius: 12 }}
              />
            )}

            {(newStepType === 'speak_page' || newStepType === 'dynamic_pause' || newStepType === 'wait') && (
              <TextInput
                mode="outlined"
                label={t('settings.dialog.add_step.time')}
                keyboardType="numeric"
                value={String(newMs)}
                onChangeText={(v) => setNewMs(Number(v) || 0)}
                style={{ height: 44 }}
                outlineStyle={{ borderRadius: 12 }}
              />
            )}

            {newStepType === 'listen_and_branch' && (
              <TextInput
                mode="outlined"
                label={t('settings.dialog.add_step.threshold')}
                keyboardType="numeric"
                value={String(newThreshold)}
                onChangeText={(v) => setNewThreshold(Number(v) || 0)}
                style={{ height: 44 }}
                outlineStyle={{ borderRadius: 12 }}
              />
            )}
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setStepDialogOpen(false)}>{t('btn.cancel')}</Button>
            <Button mode="contained" onPress={confirmAddStep}>
              {t('btn.add')}
            </Button>
          </Dialog.Actions>
        </Dialog>

        {/* Group Delete Confirmation Dialog */}
        <Dialog visible={deleteDialogOpen} onDismiss={() => setDeleteDialogOpen(false)}>
          <Dialog.Title>{t('settings.delete_btn')}</Dialog.Title>
          <Dialog.Content>
            <Text style={{ marginBottom: 12 }}>
              {t('settings.dialog.delete.confirm_text', { name: activeGroup.name })}
            </Text>
            <TextInput
              mode="outlined"
              value={deleteConfirmText}
              onChangeText={setDeleteConfirmText}
              placeholder="DELETE"
              style={{ height: 44 }}
              outlineStyle={{ borderRadius: 12 }}
            />
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setDeleteDialogOpen(false)}>{t('btn.cancel')}</Button>
            <Button
              mode="contained"
              buttonColor={theme.colors.error}
              onPress={handleDeleteGroup}
              disabled={deleteConfirmText !== 'DELETE'}
            >
              {t('btn.delete')}
            </Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    paddingHorizontal: 16,
    paddingTop: 48,
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  scrollContent: {
    paddingBottom: 64,
    gap: 12,
  },
  input: {
    height: 48,
  },
  divider: {
    marginVertical: 12,
  },
  sectionTitle: {
    fontWeight: 'bold',
    marginBottom: 4,
  },
  counterRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 8,
  },
  counterButtons: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  counterText: {
    fontSize: 18,
    fontWeight: 'bold',
    minWidth: 24,
    textAlign: 'center',
  },
  columnRow: {
    flexDirection: 'row',
    gap: 8,
    alignItems: 'center',
    marginBottom: 8,
  },
  sortButtons: {
    flexDirection: 'column',
    alignItems: 'center',
  },
  sortBtn: {
    margin: 0,
    height: 20,
    width: 20,
  },
  pageNameInput: {
    flex: 1,
    height: 40,
  },
  langBtn: {
    minWidth: 100,
    height: 40,
    justifyContent: 'center',
  },
  dropdownAnchor: {
    alignSelf: 'stretch',
    marginBottom: 12,
  },
  stepsSection: {
    marginTop: 8,
  },
  stepsCard: {
    borderRadius: 20,
    overflow: 'hidden',
  },
  stepControls: {
    flexDirection: 'row',
  },
  stepControlBtn: {
    margin: 0,
  },
  createModeBtn: {
    marginTop: 8,
    borderRadius: 100,
  },
  createModeContainer: {
    padding: 16,
    borderRadius: 20,
    marginTop: 8,
  },
  customStepRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 4,
  },
  createModeActions: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 12,
  },
});

import React from 'react';
import { StyleSheet } from 'react-native';
import { Dialog, Button, TextInput, Menu } from 'react-native-paper';

interface Props {
  visible: boolean;
  onDismiss: () => void;
  newStepType: string;
  setNewStepType: (type: string) => void;
  newStepTypeVisible: boolean;
  setNewStepTypeVisible: (visible: boolean) => void;
  newPageIdx: number;
  setNewPageIdx: (idx: number) => void;
  newMs: number;
  setNewMs: (ms: number) => void;
  newThreshold: number;
  setNewThreshold: (threshold: number) => void;
  confirmAddStep: () => void;
  t: (key: string, replacements?: any) => string;
  stepLabels: Record<string, string>;
}

export function AddStepDialog({
  visible,
  onDismiss,
  newStepType,
  setNewStepType,
  newStepTypeVisible,
  setNewStepTypeVisible,
  newPageIdx,
  setNewPageIdx,
  newMs,
  setNewMs,
  newThreshold,
  setNewThreshold,
  confirmAddStep,
  t,
  stepLabels,
}: Props) {
  return (
    <Dialog visible={visible} onDismiss={onDismiss}>
      <Dialog.Title>{t('settings.dialog.add_step.title')}</Dialog.Title>
      <Dialog.Content style={styles.dialogContent}>
        <Menu
          visible={newStepTypeVisible}
          onDismiss={() => setNewStepTypeVisible(false)}
          anchor={
            <Button
              mode="outlined"
              onPress={() => setNewStepTypeVisible(true)}
              accessibilityLabel="Select step type"
            >
              {`${t('settings.dialog.add_step.type')}: ${stepLabels[newStepType] || newStepType}`}
            </Button>
          }
        >
          {Object.entries(stepLabels).map(([k, v]) => (
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
            style={styles.input}
            outlineStyle={{ borderRadius: 12 }}
            accessibilityLabel="Page index input"
          />
        )}

        {(newStepType === 'speak_page' ||
          newStepType === 'dynamic_pause' ||
          newStepType === 'wait') && (
          <TextInput
            mode="outlined"
            label={t('settings.dialog.add_step.time')}
            keyboardType="numeric"
            value={String(newMs)}
            onChangeText={(v) => setNewMs(Number(v) || 0)}
            style={styles.input}
            outlineStyle={{ borderRadius: 12 }}
            accessibilityLabel="Duration in milliseconds input"
          />
        )}

        {newStepType === 'listen_and_branch' && (
          <TextInput
            mode="outlined"
            label={t('settings.dialog.add_step.threshold')}
            keyboardType="numeric"
            value={String(newThreshold)}
            onChangeText={(v) => setNewThreshold(Number(v) || 0)}
            style={styles.input}
            outlineStyle={{ borderRadius: 12 }}
            accessibilityLabel="Success threshold input"
          />
        )}
      </Dialog.Content>
      <Dialog.Actions>
        <Button onPress={onDismiss}>{t('btn.cancel')}</Button>
        <Button mode="contained" onPress={confirmAddStep} accessibilityLabel="Add step button">
          {t('btn.add')}
        </Button>
      </Dialog.Actions>
    </Dialog>
  );
}

const styles = StyleSheet.create({
  dialogContent: {
    gap: 12,
  },
  input: {
    height: 44,
  },
});

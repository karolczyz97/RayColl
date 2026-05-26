import React from 'react';
import { View, StyleSheet } from 'react-native';
import { TextInput, Text, IconButton, Button, useTheme } from 'react-native-paper';
import type { ModeStep } from '../../types/models';

interface Props {
  newModeName: string;
  setNewModeName: (v: string) => void;
  customSteps: ModeStep[];
  setCustomSteps: React.Dispatch<React.SetStateAction<ModeStep[]>>;
  saveCustomMode: () => void;
  setStepDialogOpen: (v: boolean) => void;
  setEditingModeId: (id: string | null) => void;
  t: (key: string, replacements?: any) => string;
  stepSummary: (step: ModeStep, t: any) => string;
}

export function CreateStudyModeSection({
  newModeName,
  setNewModeName,
  customSteps,
  setCustomSteps,
  saveCustomMode,
  setStepDialogOpen,
  setEditingModeId,
  t,
  stepSummary,
}: Props) {
  const theme = useTheme();

  return (
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
            accessibilityLabel={`Move step ${i + 1} up`}
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
            accessibilityLabel={`Move step ${i + 1} down`}
          />
          <IconButton
            icon="delete"
            size={16}
            iconColor={theme.colors.error}
            onPress={() => setCustomSteps((s) => s.filter((_, j) => j !== i))}
            accessibilityLabel={`Delete step ${i + 1}`}
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
          accessibilityLabel="Add new step"
        >
          {t('settings.add_step_btn')}
        </Button>
        <Button
          mode="contained"
          onPress={saveCustomMode}
          disabled={!newModeName.trim() || customSteps.length === 0}
          accessibilityLabel="Save study mode"
        >
          {t('settings.save_mode_btn')}
        </Button>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
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

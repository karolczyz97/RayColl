import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Divider, List, Button } from 'react-native-paper';
import type { StudyMode, ModeStep } from '@/types/models';
import { useI18n, type TranslationFn } from '@/i18n';
import { TOKENS } from '@/theme/tokens';
import { SectionCard } from '@/components/layout/SectionCard';
import { getModeName } from '@/i18n/modeHelpers';
import { StepReorderControls } from './StepReorderControls';

interface StudyModeStepsEditorProps {
  activeMode: StudyMode;
  isDefaultMode: boolean;
  hasCustomSteps: boolean;
  moveStep: (mode: StudyMode, index: number, dir: -1 | 1) => void;
  deleteStep: (mode: StudyMode, index: number) => void;
  addStepToMode: (mode: StudyMode) => void;
  onResetMode: (mode: StudyMode) => void;
  formatStepSummary: (step: ModeStep, t: TranslationFn) => string;
}

export function StudyModeStepsEditor({
  activeMode,
  isDefaultMode,
  hasCustomSteps,
  moveStep,
  deleteStep,
  addStepToMode,
  onResetMode,
  formatStepSummary,
}: StudyModeStepsEditorProps) {
  const { t } = useI18n();

  return (
    <SectionCard title={t('settings.mode_steps', { name: getModeName(t, activeMode.id, activeMode.name) })}>
        <View style={styles.stepsList}>
          {activeMode.steps.map((step, index) => (
            <View key={step.id ?? index}>
              {index > 0 && <Divider style={styles.divider} />}
              <List.Item
                style={styles.listItem}
                title={`${index + 1}. ${formatStepSummary(step, t)}`}
                right={() => (
                  <StepReorderControls
                    index={index}
                    isFirst={index === 0}
                    isLast={index === activeMode.steps.length - 1}
                    onMoveUp={() => moveStep(activeMode, index, -1)}
                    onMoveDown={() => moveStep(activeMode, index, 1)}
                    onDelete={() => deleteStep(activeMode, index)}
                  />
                )}
              />
            </View>
          ))}
        </View>
        <View style={styles.footer}>
          <Button
            icon="plus"
            mode="text"
            onPress={() => addStepToMode(activeMode)}
            accessibilityLabel="Add step"
          >
            {t('settings.add_step_btn')}
          </Button>
          {hasCustomSteps && (
            <Button
              icon="restore"
              mode="text"
              onPress={() => onResetMode(activeMode)}
              accessibilityLabel="Reset mode to default"
            >
              {t('settings.reset_mode_btn')}
            </Button>
          )}
        </View>
    </SectionCard>
  );
}

const styles = StyleSheet.create({
  stepsList: {
    borderRadius: TOKENS.control.borderRadius,
    overflow: 'hidden',
  },
  divider: {
    marginVertical: TOKENS.spacing.xs,
  },
  listItem: {
    paddingVertical: TOKENS.spacing.xs,
    paddingHorizontal: 0,
  },
  footer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: TOKENS.spacing.sm,
  },
});

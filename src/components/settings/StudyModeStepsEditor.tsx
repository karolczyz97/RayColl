import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Divider, List, IconButton, Button, useTheme } from 'react-native-paper';
import type { StudyMode, ModeStep } from '../../types/models';
import type { TranslationFn } from '../../i18n';
import { TOKENS } from '../../theme/tokens';
import { SectionCard } from '../layout/SectionCard';
import { getModeName } from '../../i18n/modeHelpers';

interface Props {
  activeMode: StudyMode;
  isDefaultMode: boolean;
  hasCustomSteps: boolean;
  moveStep: (mode: StudyMode, index: number, dir: -1 | 1) => void;
  deleteStep: (mode: StudyMode, index: number) => void;
  addStepToMode: (mode: StudyMode) => void;
  onResetMode: (mode: StudyMode) => void;
  t: TranslationFn;
  stepSummary: (step: ModeStep, t: TranslationFn) => string;
}

export function StudyModeStepsEditor({
  activeMode,
  isDefaultMode,
  hasCustomSteps,
  moveStep,
  deleteStep,
  addStepToMode,
  onResetMode,
  t,
  stepSummary,
}: Props) {
  const theme = useTheme();

  return (
    <SectionCard title={t('settings.mode_steps', { name: getModeName(t, activeMode.id, activeMode.name) })}>
        <View style={styles.stepsList}>
          {activeMode.steps.map((step, index) => (
            <View key={index}>
              {index > 0 && <Divider style={styles.divider} />}
              <List.Item
                style={styles.listItem}
                title={`${index + 1}. ${stepSummary(step, t)}`}
                right={() => (
                  <View style={styles.stepControls}>
                    <IconButton
                      icon="arrow-up"
                      size={16}
                      style={styles.stepControlBtn}
                      onPress={() => moveStep(activeMode, index, -1)}
                      disabled={index === 0}
                      accessibilityLabel={`Move step ${index + 1} up`}
                    />
                    <IconButton
                      icon="arrow-down"
                      size={16}
                      style={styles.stepControlBtn}
                      onPress={() => moveStep(activeMode, index, 1)}
                      disabled={index === activeMode.steps.length - 1}
                      accessibilityLabel={`Move step ${index + 1} down`}
                    />
                    <IconButton
                      icon="delete"
                      size={16}
                      style={styles.stepControlBtn}
                      iconColor={theme.colors.error}
                      onPress={() => deleteStep(activeMode, index)}
                      accessibilityLabel={`Delete step ${index + 1}`}
                    />
                  </View>
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
  stepControls: {
    flexDirection: 'row',
  },
  stepControlBtn: {
    margin: 0,
  },
  footer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginTop: TOKENS.spacing.sm,
  },
});

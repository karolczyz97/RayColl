import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Text, Card, Divider, List, IconButton, Button, useTheme } from 'react-native-paper';
import type { StudyMode, ModeStep } from '../../types/models';

interface Props {
  activeMode: StudyMode;
  isDefaultMode: boolean;
  moveStep: (mode: StudyMode, i: number, dir: -1 | 1) => void;
  deleteStep: (mode: StudyMode, i: number) => void;
  addStepToMode: (mode: StudyMode) => void;
  t: (key: string, replacements?: any) => string;
  stepSummary: (step: ModeStep, t: any) => string;
}

export function StudyModeStepsEditor({
  activeMode,
  isDefaultMode,
  moveStep,
  deleteStep,
  addStepToMode,
  t,
  stepSummary,
}: Props) {
  const theme = useTheme();

  const getModeName = (m: StudyMode) => {
    const key = `mode.${m.id}.name`;
    const translated = t(key);
    return translated === key ? m.name : translated;
  };

  return (
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
                        accessibilityLabel={`Move step ${i + 1} up`}
                      />
                      <IconButton
                        icon="arrow-down"
                        size={16}
                        style={styles.stepControlBtn}
                        onPress={() => moveStep(activeMode, i, 1)}
                        disabled={i === activeMode.steps.length - 1}
                        accessibilityLabel={`Move step ${i + 1} down`}
                      />
                      <IconButton
                        icon="delete"
                        size={16}
                        style={styles.stepControlBtn}
                        iconColor={theme.colors.error}
                        onPress={() => deleteStep(activeMode, i)}
                        accessibilityLabel={`Delete step ${i + 1}`}
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
        <Button
          icon="plus"
          mode="text"
          onPress={() => addStepToMode(activeMode)}
          style={{ alignSelf: 'flex-start' }}
          accessibilityLabel="Add step"
        >
          {t('settings.add_step_btn')}
        </Button>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
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
});

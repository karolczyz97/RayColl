import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Text, IconButton } from 'react-native-paper';
import { AppSelect } from '../AppSelect';
import { TOKENS } from '../../theme/tokens';
import type { StudyMode } from '../../types/models';
import type { TranslationFn } from '../../i18n';

interface Props {
  activeModeId: string;
  onModeChange: (modeId: string) => void;
  studyModes: StudyMode[];
  t: TranslationFn;
  onCreateMode?: () => void;
}

export function StudyModeSelector({ activeModeId, onModeChange, studyModes, t, onCreateMode }: Props) {
  const getModeName = (mode: StudyMode) => {
    const key = `mode.${mode.id}.name`;
    const translated = t(key);
    return translated === key ? mode.name : translated;
  };

  const options = studyModes.map((mode) => ({
    label: getModeName(mode),
    value: mode.id,
  }));

  return (
    <View style={styles.container}>
      <Text variant="titleMedium" style={styles.sectionTitle}>
        {t('settings.active_mode')}
      </Text>
      <View style={styles.selectorRow}>
        <View style={styles.selectWrapper}>
          <AppSelect
            value={activeModeId}
            options={options}
            onChange={onModeChange}
            accessibilityLabel="Active study mode selection"
          />
        </View>
        {onCreateMode && (
          <IconButton
            icon="plus"
            mode="contained"
            onPress={onCreateMode}
            style={styles.plusButton}
            size={24}
            accessibilityLabel="Create study mode button"
          />
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: TOKENS.spacing.xs,
    width: '100%',
  },
  sectionTitle: {
    fontWeight: 'bold',
    marginBottom: TOKENS.spacing.xs,
  },
  selectorRow: {
    flexDirection: 'row',
    alignItems: 'center',
    width: '100%',
    marginBottom: TOKENS.spacing.md,
  },
  selectWrapper: {
    flex: 1,
  },
  plusButton: {
    marginLeft: TOKENS.spacing.sm,
    margin: 0,
    height: TOKENS.touchTarget.min,
    width: TOKENS.touchTarget.min,
    borderRadius: TOKENS.control.roundedBorderRadius,
  },
});

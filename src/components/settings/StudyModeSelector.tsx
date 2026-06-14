import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Text, IconButton } from 'react-native-paper';
import { AppSelect } from '@/components/AppSelect';
import { TOKENS } from '@/theme/tokens';
import type { StudyMode } from '@/types/models';
import { useI18n } from '@/i18n';
import { getModeName } from '@/i18n/modeHelpers';

interface StudyModeSelectorProps {
  activeModeId: string;
  onModeChange: (modeId: string) => void;
  studyModes: StudyMode[];
  onCreateMode?: () => void;
  onEditMode?: () => void;
}

export function StudyModeSelector({
  activeModeId,
  onModeChange,
  studyModes,
  onCreateMode,
  onEditMode,
}: StudyModeSelectorProps) {
  const { t } = useI18n();
  const options = studyModes.map((mode) => ({
    label: getModeName(t, mode.id, mode.name),
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
        {onEditMode && (
          <IconButton
            icon="pencil"
            mode="contained"
            onPress={onEditMode}
            style={styles.plusButton}
            size={TOKENS.iconSize.md}
            accessibilityLabel="Edit study mode"
          />
        )}
        {onCreateMode && (
          <IconButton
            icon="plus"
            mode="contained"
            onPress={onCreateMode}
            style={styles.plusButton}
            size={TOKENS.iconSize.md}
            accessibilityLabel="Create study mode button"
          />
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: TOKENS.spacing.md,
    width: '100%',
  },
  sectionTitle: {
    fontWeight: TOKENS.typography.weight.bold,
  },
  selectorRow: {
    flexDirection: 'row',
    alignItems: 'center',
    width: '100%',
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

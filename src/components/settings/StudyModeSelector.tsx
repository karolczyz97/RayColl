import React, { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Text, Button, Menu } from 'react-native-paper';
import type { StudyMode } from '../../types/models';

interface Props {
  activeModeId: string;
  onModeChange: (modeId: string) => void;
  studyModes: StudyMode[];
  t: (key: string, replacements?: any) => string;
}

export function StudyModeSelector({ activeModeId, onModeChange, studyModes, t }: Props) {
  const [modeMenuVisible, setModeMenuVisible] = useState(false);

  const activeMode = studyModes.find((m) => m.id === activeModeId);

  const getModeName = (m: StudyMode) => {
    const key = `mode.${m.id}.name`;
    const translated = t(key);
    return translated === key ? m.name : translated;
  };

  return (
    <View style={styles.container}>
      <Text variant="titleMedium" style={styles.sectionTitle}>
        {t('settings.active_mode')}
      </Text>
      <Menu
        visible={modeMenuVisible}
        onDismiss={() => setModeMenuVisible(false)}
        anchor={
          <Button
            mode="outlined"
            onPress={() => setModeMenuVisible(true)}
            style={styles.dropdownAnchor}
            accessibilityLabel="Active study mode selection"
          >
            {activeMode ? getModeName(activeMode) : t('settings.modes_title')}
          </Button>
        }
      >
        {studyModes.map((m) => (
          <Menu.Item
            key={m.id}
            onPress={() => {
              onModeChange(m.id);
              setModeMenuVisible(false);
            }}
            title={getModeName(m)}
          />
        ))}
      </Menu>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 8,
  },
  sectionTitle: {
    fontWeight: 'bold',
    marginBottom: 4,
  },
  dropdownAnchor: {
    alignSelf: 'stretch',
    marginBottom: 12,
  },
});

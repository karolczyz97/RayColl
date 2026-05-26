import React, { useState } from 'react';
import { View, StyleSheet, Pressable } from 'react-native';
import { Text, Menu, useTheme } from 'react-native-paper';
import { AppIcon } from '../AppIcon';
import { useFlashcardStore } from '../../hooks/useFlashcardStore';
import { useI18n } from '../../i18n';
import type { FlashcardGroup } from '../../types/models';

interface Props {
  group: FlashcardGroup;
  onStudy: () => void;
  onModeChange: (modeId: string) => void;
}

export function StudyModeMenuButton({ group, onStudy, onModeChange }: Props) {
  const store = useFlashcardStore();
  const { t } = useI18n();
  const [visible, setVisible] = useState(false);
  const theme = useTheme();

  const dueCount = store.getDueCards(group.id).length;
  const activeMode = store.studyModes.find((m) => m.id === group.activeModeId);

  const getModeName = (mId: string, defaultName: string) => {
    const key = `mode.${mId}.name`;
    const translated = t(key);
    return translated === key ? defaultName : translated;
  };

  const modeName = activeMode ? getModeName(activeMode.id, activeMode.name) : t('mode.classic.name');

  const btnBgColor = dueCount === 0 ? theme.colors.surfaceVariant : theme.colors.primary;
  const btnTextColor = dueCount === 0 ? theme.colors.onSurfaceVariant : theme.colors.onPrimary;

  return (
    <View style={styles.studyButtonGroup}>
      <View style={[styles.pillContainer, { backgroundColor: btnBgColor }]}>
        {/* Left Action Button */}
        <Pressable
          disabled={dueCount === 0}
          onPress={onStudy}
          style={({ pressed }) => [
            styles.pillLeft,
            pressed && styles.pressed,
          ]}
          accessibilityLabel={`Start study in ${modeName} mode`}
        >
          <AppIcon name="play" size={16} color={btnTextColor} style={{ marginRight: 6 }} />
          <Text style={[styles.pillText, { color: btnTextColor }]} numberOfLines={1}>
            {modeName}
          </Text>
        </Pressable>

        {/* Divider Line */}
        <View style={[styles.pillDivider, { backgroundColor: btnTextColor }]} />

        {/* Right Dropdown Anchor */}
        <Menu
          visible={visible}
          onDismiss={() => setVisible(false)}
          anchor={
            <Pressable
              onPress={() => setVisible(true)}
              style={({ pressed }) => [
                styles.pillRight,
                pressed && styles.pressed,
              ]}
              accessibilityLabel="Select study mode"
            >
              <AppIcon name="chevron-down" size={16} color={btnTextColor} />
            </Pressable>
          }
        >
          {store.studyModes.map((m) => (
            <Menu.Item
              key={m.id}
              title={getModeName(m.id, m.name)}
              leadingIcon={m.id === group.activeModeId ? 'check' : undefined}
              onPress={() => {
                onModeChange(m.id);
                setVisible(false);
              }}
            />
          ))}
        </Menu>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  studyButtonGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    width: '100%',
  },
  pillContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 20,
    height: 40,
    width: '100%',
    overflow: 'hidden',
  },
  pillLeft: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    height: '100%',
    paddingLeft: 16,
    paddingRight: 8,
  },
  pillText: {
    fontSize: 13,
    fontWeight: 'bold',
  },
  pillDivider: {
    width: 1,
    height: '60%',
    opacity: 0.25,
  },
  pillRight: {
    width: 44,
    height: '100%',
    alignItems: 'center',
    justifyContent: 'center',
  },
  pressed: {
    opacity: 0.75,
  },
});

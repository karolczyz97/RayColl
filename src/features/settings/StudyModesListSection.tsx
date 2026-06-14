import React, { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { IconButton, useTheme } from 'react-native-paper';
import { router } from 'expo-router';
import type { StudyMode } from '@/types/models';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { useI18n } from '@/i18n';
import { getModeName } from '@/i18n/modeHelpers';
import { ROUTES } from '@/constants/routes';
import { TOKENS } from '@/theme/tokens';
import { AppIcon } from '@/components/AppIcon';
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog';
import { SettingsSection, SettingsTile } from '@/components/settings/SettingsSection';

export function StudyModesListSection() {
  const { t } = useI18n();
  const theme = useTheme();
  const store = useFlashcardStore();
  const [modeToDelete, setModeToDelete] = useState<StudyMode | null>(null);

  const handleDeleteConfirm = () => {
    if (modeToDelete) {
      store.deleteStudyMode(modeToDelete.id);
    }
    setModeToDelete(null);
  };

  return (
    <>
      <SettingsSection title={t('app_settings.study_modes')}>
        {store.studyModes.map((mode) => (
          <SettingsTile
            key={mode.id}
            title={getModeName(t, mode.id, mode.name)}
            description={
              t('study_modes.steps_count', { count: mode.steps.length }) +
              (mode.isBuiltIn ? ` - ${t('study_modes.built_in')}` : '')
            }
            onPress={() => router.navigate(ROUTES.studyMode(mode.id))}
            trailing={
              <View style={styles.itemActions}>
                {!mode.isBuiltIn && (
                  <IconButton
                    icon="delete-outline"
                    size={TOKENS.iconSize.md}
                    iconColor={theme.colors.error}
                    onPress={(event) => {
                      event.stopPropagation();
                      setModeToDelete(mode);
                    }}
                    accessibilityLabel={t('study_modes.delete_title')}
                    style={styles.deleteBtn}
                  />
                )}
                <AppIcon
                  name="chevron-right"
                  size={TOKENS.iconSize.md}
                  color={theme.colors.onSurfaceVariant}
                />
              </View>
            }
          />
        ))}
        <SettingsTile
          icon="plus"
          accent
          title={t('settings.create_mode_btn')}
          onPress={() => router.navigate(ROUTES.createStudyMode())}
        />
      </SettingsSection>

      <ConfirmDialog
        visible={!!modeToDelete}
        onDismiss={() => setModeToDelete(null)}
        onConfirm={handleDeleteConfirm}
        title={t('study_modes.delete_title')}
        message={t('study_modes.delete_message', {
          name: modeToDelete ? getModeName(t, modeToDelete.id, modeToDelete.name) : '',
        })}
        confirmLabel={t('study_modes.delete_title')}
        cancelLabel={t('btn.cancel')}
        destructive
      />
    </>
  );
}

const styles = StyleSheet.create({
  itemActions: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  deleteBtn: {
    margin: 0,
  },
});

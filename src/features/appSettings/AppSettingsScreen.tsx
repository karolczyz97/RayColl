import React from 'react';
import { StyleSheet, TouchableOpacity, View, Platform } from 'react-native';
import { Button, Switch, Text, useTheme } from 'react-native-paper';
import { ExpressiveButtonGroup } from '@/components/expressive';
import { AppSnackbar } from '@/components/feedback/AppSnackbar';
import { SyncStatusBanner } from '@/components/feedback/SyncStatusBanner';
import { AppSelect } from '@/components/AppSelect';
import { AnimatedSection } from '@/components/layout/AnimatedSection';
import { AppScreen } from '@/components/layout/AppScreen';
import { LoadingState } from '@/components/layout/LoadingState';
import { SettingsSection, SettingsTile } from '@/components/settings/SettingsSection';
import { isThemePref } from '@/contexts/UserPreferencesContext';
import { useI18n } from '@/i18n';
import { TOKENS } from '@/theme/tokens';
import { ChangelogDialog } from '@/components/feedback/ChangelogDialog';
import { StudyModesListSection } from '@/features/settings/StudyModesListSection';
import {
  LANGUAGE_OPTIONS,
  isLanguageCode,
  useAppSettingsController,
} from './useAppSettingsController';

export function AppSettingsScreen() {
  const { t } = useI18n();
  const theme = useTheme();
  const {
    changelogVisible,
    formMaxWidth,
    handleBack,
    handleExport,
    handleImportFromFile,
    handleTtsRateChange,
    isImporting,
    isLoading,
    language,
    setChangelogVisible,
    setLanguage,
    setSnackbarMessage,
    setThemePref,
    setUseSystemColors,
    snackbarMessage,
    store,
    themePref,
    ttsRate,
    useSystemColors,
    versionLines,
  } = useAppSettingsController();

  if (isLoading) {
    return <LoadingState />;
  }

  return (
    <AppScreen
      title={t('app_settings.title')}
      onBack={handleBack}
      maxWidth={formMaxWidth}
    >
      <SyncStatusBanner
        syncStatus={store.syncStatus}
        lastSyncError={store.lastSyncError}
        lastPersistenceError={store.lastPersistenceError}
        lastStoreError={store.lastStoreError}
      />

      <AnimatedSection order={0}>
        <SettingsSection title={t('app_settings.section.appearance')}>
          <SettingsTile title={t('app_settings.theme')}>
            <ExpressiveButtonGroup
              value={themePref}
              onValueChange={(value) => {
                if (isThemePref(value)) {
                  setThemePref(value);
                }
              }}
              buttons={[
                { value: 'light', label: t('app_settings.theme.light'), icon: 'weather-sunny' },
                {
                  value: 'system',
                  label: t('app_settings.theme.system'),
                  icon: 'theme-light-dark',
                },
                { value: 'dark', label: t('app_settings.theme.dark'), icon: 'weather-night' },
              ]}
            />
          </SettingsTile>

          {Platform.OS === 'android' && (
            <SettingsTile
              title={t('app_settings.dynamic_colors.title')}
              description={t('app_settings.dynamic_colors.desc')}
              onPress={() => setUseSystemColors(!useSystemColors)}
              trailing={
                <View pointerEvents="none">
                  <Switch value={useSystemColors} />
                </View>
              }
            />
          )}
        </SettingsSection>
      </AnimatedSection>

      <AnimatedSection order={1}>
        <SettingsSection title={t('app_settings.section.language_speech')}>
          <SettingsTile title={t('app_settings.lang')}>
            <AppSelect
              value={language}
              options={LANGUAGE_OPTIONS}
              onChange={(value) => {
                if (isLanguageCode(value)) {
                  setLanguage(value);
                }
              }}
              accessibilityLabel="Select app language"
            />
          </SettingsTile>

          <SettingsTile
            title={t('app_settings.tts_rate')}
            trailing={
              <Text variant="titleMedium" style={[styles.valueText, { color: theme.colors.primary }]}>
                {ttsRate.toFixed(1)}x
              </Text>
            }
          >
            <ExpressiveButtonGroup
              value={String(ttsRate)}
              onValueChange={(value) => void handleTtsRateChange(parseFloat(value))}
              buttons={[
                { value: '0.7', label: '0.7x' },
                { value: '1', label: '1.0x' },
                { value: '1.3', label: '1.3x' },
                { value: '1.6', label: '1.6x' },
              ]}
            />
          </SettingsTile>
        </SettingsSection>
      </AnimatedSection>

      <AnimatedSection order={2}>
        <StudyModesListSection />
      </AnimatedSection>

      <AnimatedSection order={3}>
        <SettingsSection title={t('app_settings.export_import')}>
          <SettingsTile title={t('app_settings.export_import')}>
            <View style={styles.actionButtonsRow}>
              <Button
                mode="contained-tonal"
                icon="share-variant"
                onPress={() => void handleExport()}
                style={styles.actionButton}
              >
                {t('app_settings.export_btn')}
              </Button>
              <Button
                mode="contained-tonal"
                icon="file-upload"
                onPress={() => void handleImportFromFile()}
                disabled={isImporting}
                style={styles.actionButton}
              >
                {t('app_settings.import_file_btn')}
              </Button>
            </View>
          </SettingsTile>
        </SettingsSection>
      </AnimatedSection>

      <TouchableOpacity
        style={styles.versionFooter}
        onPress={() => setChangelogVisible(true)}
        activeOpacity={0.6}
        accessibilityRole="button"
        accessibilityLabel={t('update.show_changes')}
      >
        {versionLines.map((line) => (
          <Text
            key={line}
            variant="labelSmall"
            style={[styles.versionLine, { color: theme.colors.onSurfaceVariant }]}
          >
            {line}
          </Text>
        ))}
      </TouchableOpacity>
      <ChangelogDialog visible={changelogVisible} onDismiss={() => setChangelogVisible(false)} />

      <AppSnackbar
        visible={!!snackbarMessage}
        message={snackbarMessage}
        onDismiss={() => setSnackbarMessage('')}
      />
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  valueText: {
    fontWeight: TOKENS.typography.weight.bold,
  },
  actionButtonsRow: {
    flexDirection: 'row',
    gap: TOKENS.spacing.md,
  },
  actionButton: {
    flex: 1,
  },
  versionFooter: {
    alignSelf: 'flex-end',
    marginTop: TOKENS.spacing.lg,
    alignItems: 'flex-end',
    opacity: TOKENS.opacity.muted,
  },
  versionLine: {
    textAlign: 'right',
  },
});

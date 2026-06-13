import React from 'react';
import { StyleSheet, TouchableOpacity, View } from 'react-native';
import { Button, SegmentedButtons, Text, useTheme } from 'react-native-paper';
import { router } from 'expo-router';
import { ROUTES } from '@/constants/routes';
import { AppSnackbar } from '@/components/feedback/AppSnackbar';
import { SyncStatusBanner } from '@/components/feedback/SyncStatusBanner';
import { AppSelect } from '@/components/AppSelect';
import { AnimatedSection } from '@/components/layout/AnimatedSection';
import { AppScreen } from '@/components/layout/AppScreen';
import { LoadingState } from '@/components/layout/LoadingState';
import { SectionCard } from '@/components/layout/SectionCard';
import { isThemePref } from '@/contexts/UserPreferencesContext';
import { useI18n } from '@/i18n';
import { TOKENS } from '@/theme/tokens';
import { ChangelogDialog } from '@/components/feedback/ChangelogDialog';
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
    <AppScreen title={t('app_settings.title')} onBack={handleBack} maxWidth={formMaxWidth}>
      <SyncStatusBanner
        syncStatus={store.syncStatus}
        lastSyncError={store.lastSyncError}
        lastPersistenceError={store.lastPersistenceError}
        lastStoreError={store.lastStoreError}
      />

      <AnimatedSection order={0}>
        <SectionCard title={t('app_settings.lang')}>
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
        </SectionCard>
      </AnimatedSection>

      <AnimatedSection order={1}>
        <SectionCard title={t('app_settings.theme')}>
          <SegmentedButtons
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
            style={styles.segmentedButtons}
          />
        </SectionCard>
      </AnimatedSection>

      <AnimatedSection order={2}>
        <SectionCard title={t('app_settings.dynamic_colors.title')}>
          <Text variant="bodyMedium" style={styles.mutedText}>
            {t('app_settings.dynamic_colors.desc')}
          </Text>
          <SegmentedButtons
            value={useSystemColors ? 'true' : 'false'}
            onValueChange={(value) => {
              if (value === 'true' || value === 'false') {
                setUseSystemColors(value === 'true');
              }
            }}
            buttons={[
              {
                value: 'true',
                label: t('app_settings.dynamic_colors.enabled'),
                icon: 'palette',
              },
              {
                value: 'false',
                label: t('app_settings.dynamic_colors.disabled'),
                icon: 'palette-swatch-outline',
              },
            ]}
            style={styles.segmentedButtons}
          />
        </SectionCard>
      </AnimatedSection>

      <AnimatedSection order={3}>
        <SectionCard title={t('app_settings.tts_rate')}>
          <Text variant="bodyLarge" style={styles.valueText}>
            {ttsRate.toFixed(1)}x
          </Text>
          <SegmentedButtons
            value={String(ttsRate)}
            onValueChange={(value) => void handleTtsRateChange(parseFloat(value))}
            buttons={[
              { value: '0.7', label: '0.7x' },
              { value: '1', label: '1.0x' },
              { value: '1.3', label: '1.3x' },
              { value: '1.6', label: '1.6x' },
            ]}
          />
        </SectionCard>
      </AnimatedSection>

      <AnimatedSection order={4}>
        <SectionCard title={t('app_settings.study_modes')}>
          <Text variant="bodyMedium" style={styles.mutedText}>
            {t('app_settings.study_modes.desc')}
          </Text>
          <View style={styles.sectionActionRow}>
            <Button
              mode="contained-tonal"
              icon="chevron-right"
              onPress={() => router.navigate(ROUTES.STUDY_MODES)}
              accessibilityLabel={t('app_settings.study_modes')}
            >
              {t('app_settings.study_modes.open')}
            </Button>
          </View>
        </SectionCard>
      </AnimatedSection>

      <AnimatedSection order={5}>
        <SectionCard title={t('app_settings.export_import')}>
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
        </SectionCard>
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
  segmentedButtons: {
    width: '100%',
  },
  mutedText: {
    marginBottom: TOKENS.spacing.md,
  },
  valueText: {
    fontWeight: TOKENS.typography.weight.bold,
    marginBottom: TOKENS.spacing.md,
  },
  actionButtonsRow: {
    flexDirection: 'row',
    gap: TOKENS.spacing.md,
  },
  actionButton: {
    flex: 1,
  },
  sectionActionRow: {
    flexDirection: 'row',
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

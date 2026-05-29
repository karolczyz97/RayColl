import React, { useState } from 'react';
import { Platform, StyleSheet, View } from 'react-native';
import { Button, SegmentedButtons, Text } from 'react-native-paper';
import { router } from 'expo-router';
import { ConfirmDialog } from '../../components/dialogs/ConfirmDialog';
import { TextEntryDialog } from '../../components/dialogs/TextEntryDialog';
import { AppSnackbar } from '../../components/feedback/AppSnackbar';
import { SyncStatusBanner } from '../../components/feedback/SyncStatusBanner';
import { AppSelect } from '../../components/AppSelect';
import { AnimatedSection } from '../../components/layout/AnimatedSection';
import { AppScreen } from '../../components/layout/AppScreen';
import { LoadingState } from '../../components/layout/LoadingState';
import { SectionCard } from '../../components/layout/SectionCard';
import { useAppTheme, type ThemePref } from '../../contexts/ThemeContext';
import { useFlashcardStore } from '../../hooks/useFlashcardStore';
import { useResponsiveLayout } from '../../hooks/useResponsiveLayout';
import { useI18n, type LanguageCode } from '../../i18n';
import { TOKENS } from '../../theme/tokens';

const LANGUAGE_OPTIONS = [
  { label: 'Polski', value: 'pl' },
  { label: 'English', value: 'en' },
  { label: 'Deutsch', value: 'de' },
  { label: 'Español', value: 'es' },
  { label: 'Italiano', value: 'it' },
] satisfies { label: string; value: LanguageCode }[];

const THEME_OPTIONS = ['light', 'system', 'dark'] satisfies ThemePref[];

function isLanguageCode(value: string): value is LanguageCode {
  return LANGUAGE_OPTIONS.some((option) => option.value === value);
}

function isThemePref(value: string): value is ThemePref {
  return THEME_OPTIONS.some((option) => option === value);
}

export function AppSettingsScreen() {
  const { t, language, setLanguage } = useI18n();
  const store = useFlashcardStore();
  const { themePref, setThemePref, useSystemColors, setUseSystemColors, ttsRate, setTtsRate } =
    useAppTheme();
  const { formMaxWidth } = useResponsiveLayout();
  const [importVisible, setImportVisible] = useState(false);
  const [importJson, setImportJson] = useState('');
  const [resetVisible, setResetVisible] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');

  const getLabel = (key: string, fallback: string) => {
    const translated = t(key);
    return translated !== key ? translated : fallback;
  };

  const handleTtsRateChange = async (rate: number) => {
    const clampedRate = Math.max(0.5, Math.min(2.0, rate));
    await setTtsRate(clampedRate);
  };

  const handleExport = async () => {
    try {
      const data = store.exportState();

      if (Platform.OS === 'web') {
        const blob = new Blob([data], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = 'fiszki-backup.json';
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        setTimeout(() => URL.revokeObjectURL(url), 0);
        return;
      }

      const { Share } = await import('react-native');
      await Share.share({
        message: data,
        title: 'RayColl Backup',
      });
    } catch (error) {
      console.warn('Export failed:', error);
      setSnackbarMessage(getLabel('app_settings.export_error', 'Export failed.'));
    }
  };

  const handleImport = () => {
    if (!importJson.trim()) {
      return;
    }

    try {
      store.importState(importJson);
      setImportJson('');
      setImportVisible(false);
      setSnackbarMessage(getLabel('app_settings.import_success', 'Import completed!'));
    } catch (error: unknown) {
      setSnackbarMessage(error instanceof Error ? error.message : 'Invalid backup JSON!');
    }
  };

  const handleResetConfirm = () => {
    store.resetToDefault();
    setResetVisible(false);
    setSnackbarMessage(getLabel('app_settings.reset_success', 'All data reset to defaults.'));
  };

  if (store.isLoading) {
    return <LoadingState />;
  }

  return (
    <AppScreen title={t('app_settings.title')} onBack={() => router.back()} maxWidth={formMaxWidth}>
      <SyncStatusBanner
        syncStatus={store.syncStatus}
        lastSyncError={store.lastSyncError}
        lastPersistenceError={store.lastPersistenceError}
        lastStoreError={store.lastStoreError}
        t={t}
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
              icon="import"
              onPress={() => setImportVisible(true)}
              style={styles.actionButton}
            >
              {t('app_settings.import_btn')}
            </Button>
          </View>
        </SectionCard>
      </AnimatedSection>

      <AnimatedSection order={5}>
        <SectionCard title={t('app_settings.danger_zone')} danger>
          <Text variant="bodySmall" style={styles.mutedText}>
            {t('app_settings.reset_confirm')}
          </Text>
          <Button mode="contained" onPress={() => setResetVisible(true)}>
            {t('app_settings.reset_btn')}
          </Button>
        </SectionCard>
      </AnimatedSection>

      <TextEntryDialog
        visible={importVisible}
        onDismiss={() => setImportVisible(false)}
        onConfirm={handleImport}
        title={t('app_settings.import_btn')}
        value={importJson}
        onChangeText={setImportJson}
        placeholder="Paste backup JSON string here..."
        multiline
        numberOfLines={6}
        confirmLabel={t('btn.save')}
        cancelLabel={t('btn.cancel')}
        disabled={!importJson.trim()}
      />

      <ConfirmDialog
        visible={resetVisible}
        onDismiss={() => setResetVisible(false)}
        onConfirm={handleResetConfirm}
        title={t('app_settings.reset_btn')}
        message={t('app_settings.reset_confirm')}
        confirmLabel={t('app_settings.reset_btn')}
        cancelLabel={t('btn.cancel')}
        destructive
      />

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
    fontWeight: '700',
    marginBottom: TOKENS.spacing.md,
  },
  actionButtonsRow: {
    flexDirection: 'row',
    gap: TOKENS.spacing.md,
  },
  actionButton: {
    flex: 1,
  },
});

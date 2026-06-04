import React, { useState } from 'react';
import * as Application from 'expo-application';
import { Platform, StyleSheet, TouchableOpacity, View } from 'react-native';
import { Button, SegmentedButtons, Text, useTheme } from 'react-native-paper';
import { safeBack } from '@/utils/navigation';
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog';
import { AppSnackbar } from '@/components/feedback/AppSnackbar';
import { SyncStatusBanner } from '@/components/feedback/SyncStatusBanner';
import { AppSelect } from '@/components/AppSelect';
import { AnimatedSection } from '@/components/layout/AnimatedSection';
import { AppScreen } from '@/components/layout/AppScreen';
import { LoadingState } from '@/components/layout/LoadingState';
import { SectionCard } from '@/components/layout/SectionCard';
import { useAppTheme, isThemePref } from '@/contexts/ThemeContext';
import { useFlashcardStore } from '@/hooks/useFlashcardStore';
import { useResponsiveLayout } from '@/hooks/useResponsiveLayout';
import { useI18n, type LanguageCode } from '@/i18n';
import { TOKENS } from '@/theme/tokens';
import { APP_NAME } from '@/constants/app';
import { releaseInfo } from '@/config/releaseInfo';
import { ChangelogDialog } from '@/components/feedback/ChangelogDialog';

const LANGUAGE_OPTIONS = [
  { label: 'Polski', value: 'pl' },
  { label: 'English', value: 'en' },
  { label: 'Deutsch', value: 'de' },
  { label: 'Español', value: 'es' },
  { label: 'Italiano', value: 'it' },
] satisfies { label: string; value: LanguageCode }[];

function isLanguageCode(value: string): value is LanguageCode {
  return LANGUAGE_OPTIONS.some((option) => option.value === value);
}

export function AppSettingsScreen() {
  const { t, language, setLanguage } = useI18n();
  const theme = useTheme();
  const store = useFlashcardStore();
  const { themePref, setThemePref, useSystemColors, setUseSystemColors, ttsRate, setTtsRate } =
    useAppTheme();
  const { formMaxWidth } = useResponsiveLayout();
  const [isImporting, setIsImporting] = useState(false);
  const [resetVisible, setResetVisible] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');
  const isWeb = Platform.OS === 'web';
  const apkBuild = Application.nativeBuildVersion;
  const versionLines = [
    `v${releaseInfo.appVersion} • build ${releaseInfo.webBuild}`,
    ...(!isWeb && apkBuild !== null ? [`APK build ${apkBuild}`] : []),
  ];
  const [changelogVisible, setChangelogVisible] = useState(false);

  const handleTtsRateChange = async (rate: number) => {
    const clampedRate = Math.max(0.5, Math.min(2.0, rate));
    await setTtsRate(clampedRate);
  };

  const handleExport = async () => {
    try {
      const raw = store.exportState();
      const parsed = JSON.parse(raw);
      parsed.exportedAt = new Date().toISOString();
      const data = JSON.stringify(parsed, null, 2);

      const now = new Date();
      const pad = (n: number) => n.toString().padStart(2, '0');
      const timestamp = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}-${pad(now.getHours())}-${pad(now.getMinutes())}`;
      const filename = `raycoll-backup-${timestamp}.json`;

      if (Platform.OS === 'web') {
        const blob = new Blob([data], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = filename;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        setTimeout(() => URL.revokeObjectURL(url), 0);
        return;
      }

      const { File, Paths } = await import('expo-file-system');
      const { shareAsync, isAvailableAsync } = await import('expo-sharing');

      const file = new File(Paths.cache, filename);
      file.create({ overwrite: true });
      file.write(data);

      const sharingAvailable = await isAvailableAsync();
      if (!sharingAvailable) {
        setSnackbarMessage(t('app_settings.sharing_unavailable'));
        return;
      }
      await shareAsync(file.uri, { mimeType: 'application/json', dialogTitle: `${APP_NAME} Backup` });
    } catch (error) {
      console.warn('Export failed:', error);
      setSnackbarMessage(t('app_settings.export_error'));
    }
  };

  const handleImportFromFile = async () => {
    if (isImporting) return;
    setIsImporting(true);
    try {
      const { getDocumentAsync } = await import('expo-document-picker');
      const result = await getDocumentAsync({
        type: ['application/json', 'text/plain'],
        copyToCacheDirectory: true,
      });
      if (result.canceled || !result.assets?.length) return;

      const asset = result.assets[0];
      const content =
        Platform.OS === 'web' && asset.file
          ? await asset.file.text()
          : await (await fetch(asset.uri)).text();

      await store.importState(content);
      setSnackbarMessage(t('app_settings.import_success'));
    } catch (error) {
      if (error instanceof Error && error.message.startsWith('app_settings.')) {
        setSnackbarMessage(t(error.message));
      } else if (error instanceof SyntaxError) {
        setSnackbarMessage(t('app_settings.import_error'));
      } else {
        setSnackbarMessage(error instanceof Error ? error.message : t('app_settings.import_error'));
      }
    } finally {
      setIsImporting(false);
    }
  };

  const handleResetConfirm = async () => {
    setResetVisible(false);
    try {
      await store.resetToDefault();
      setSnackbarMessage(t('app_settings.reset_success'));
    } catch {
      setSnackbarMessage(t('app_settings.reset_error'));
    }
  };

  if (store.isLoading) {
    return <LoadingState />;
  }

  return (
    <AppScreen
      title={t('app_settings.title')}
      onBack={safeBack}
      maxWidth={formMaxWidth}
    >
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

      <TouchableOpacity
        style={styles.versionFooter}
        onPress={() => setChangelogVisible(true)}
        activeOpacity={0.6}
        accessibilityRole="button"
        accessibilityLabel={t('update.whats_new')}
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

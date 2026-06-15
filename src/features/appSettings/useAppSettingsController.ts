import { useCallback, useMemo, useState } from 'react';
import * as Application from 'expo-application';
import { Platform } from 'react-native';
import { navigateUp } from '@/utils/navigation';
import { useAppTheme } from '@/contexts/UserPreferencesContext';
import { readAssetText } from '@/utils/fileReader';
import { useFlashcardStore } from '@/store/FlashcardStoreContext';
import { useResponsiveLayout } from '@/hooks/useResponsiveLayout';
import { useI18n, type LanguageCode } from '@/i18n';
import { APP_NAME } from '@/constants/app';
import { releaseInfo } from '@/config/releaseInfo';

export const LANGUAGE_OPTIONS = [
  { label: 'Polski', value: 'pl' },
  { label: 'English', value: 'en' },
  { label: 'Deutsch', value: 'de' },
  { label: 'Español', value: 'es' },
  { label: 'Italiano', value: 'it' },
] satisfies { label: string; value: LanguageCode }[];

export function isLanguageCode(value: string): value is LanguageCode {
  return LANGUAGE_OPTIONS.some((option) => option.value === value);
}

function createBackupFilename(now = new Date()): string {
  const pad = (n: number) => n.toString().padStart(2, '0');
  const timestamp = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}-${pad(now.getHours())}-${pad(now.getMinutes())}`;
  return `raycoll-backup-${timestamp}.json`;
}

export function useAppSettingsController() {
  const { t, language, setLanguage } = useI18n();
  const store = useFlashcardStore();
  const { themePref, setThemePref, useSystemColors, setUseSystemColors, ttsRate, setTtsRate } =
    useAppTheme();
  const { formMaxWidth } = useResponsiveLayout();
  const [isImporting, setIsImporting] = useState(false);
  const [snackbarMessage, setSnackbarMessage] = useState('');
  const [changelogVisible, setChangelogVisible] = useState(false);

  const isWeb = Platform.OS === 'web';
  const apkBuild = Application.nativeBuildVersion;
  const versionLines = useMemo(
    () => [
      `v${releaseInfo.appVersion} • build ${releaseInfo.webBuild}`,
      ...(!isWeb && apkBuild !== null ? [`APK build ${apkBuild}`] : []),
    ],
    [apkBuild, isWeb],
  );

  const handleTtsRateChange = useCallback(
    async (rate: number) => {
      const clampedRate = Math.max(0.5, Math.min(2.0, rate));
      await setTtsRate(clampedRate);
    },
    [setTtsRate],
  );

  const handleExport = useCallback(async () => {
    try {
      const raw = store.exportState();
      const parsed = JSON.parse(raw);
      parsed.exportedAt = new Date().toISOString();
      const data = JSON.stringify(parsed, null, 2);
      const filename = createBackupFilename();

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
      await shareAsync(file.uri, {
        mimeType: 'application/json',
        dialogTitle: `${APP_NAME} Backup`,
      });
    } catch (error) {
      console.warn('Export failed:', error);
      setSnackbarMessage(t('app_settings.export_error'));
    }
  }, [store, t]);

  const handleImportFromFile = useCallback(async () => {
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
      const content = await readAssetText(asset);

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
  }, [isImporting, store, t]);

  return {
    changelogVisible,
    formMaxWidth,
    handleBack: navigateUp,
    handleExport,
    handleImportFromFile,
    handleTtsRateChange,
    isImporting,
    isLoading: store.isLoading,
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
  };
}

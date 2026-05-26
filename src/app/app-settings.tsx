import React, { useState } from 'react';
import { View, StyleSheet, ScrollView, Platform, Share, Alert } from 'react-native';
import {
  Text,
  Button,
  useTheme,
  SegmentedButtons,
  TextInput,
  Portal,
  Dialog,
  ActivityIndicator,
} from 'react-native-paper';
import Animated, { FadeInDown } from 'react-native-reanimated';
import { router } from 'expo-router';

import { useFlashcardStore } from '../hooks/useFlashcardStore';
import { useAppTheme, type ThemePref } from '../contexts/ThemeContext';
import { useI18n } from '../i18n';
import { PageHeader } from '../components/PageHeader';
import { AppCard } from '../components/AppCard';
import { AppSelect } from '../components/AppSelect';
import { useResponsiveLayout } from '../hooks/useResponsiveLayout';
import { TOKENS } from '../theme/tokens';

export default function AppSettings() {
  const { t, language, setLanguage } = useI18n();
  const theme = useTheme();
  const store = useFlashcardStore();
  const { themePref, setThemePref, useSystemColors, setUseSystemColors, ttsRate, setTtsRate } =
    useAppTheme();
  const { formMaxWidth } = useResponsiveLayout();

  const [importVisible, setImportVisible] = useState(false);
  const [importJson, setImportJson] = useState('');
  const [resetVisible, setResetVisible] = useState(false);

  const handleTtsRateChange = async (rate: number) => {
    const clampedRate = Math.max(0.5, Math.min(2.0, rate));
    await setTtsRate(clampedRate);
  };

  const handleExport = async () => {
    const data = store.exportState();
    if (Platform.OS === 'web') {
      const blob = new Blob([data], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'fiszki-backup.json';
      a.click();
      URL.revokeObjectURL(url);
    } else {
      try {
        await Share.share({
          message: data,
          title: 'RayColl Backup',
        });
      } catch (err) {
        console.warn('Export failed:', err);
      }
    }
  };

  const handleImport = () => {
    if (!importJson.trim()) return;
    try {
      store.importState(importJson);
      setImportJson('');
      setImportVisible(false);
      if (Platform.OS === 'web') {
        alert(t('app_settings.import_success') || 'Import completed!');
      } else {
        Alert.alert('Success', t('app_settings.import_success') || 'Import completed!');
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Invalid backup JSON!';
      if (Platform.OS === 'web') {
        alert(msg);
      } else {
        Alert.alert('Error', msg);
      }
    }
  };

  const handleResetConfirm = () => {
    store.resetToDefault();
    setResetVisible(false);
    if (Platform.OS === 'web') {
      alert(t('app_settings.reset_success') || 'All data reset to defaults.');
    } else {
      Alert.alert('Success', t('app_settings.reset_success') || 'All data reset to defaults.');
    }
  };

  const handleBack = () => {
    router.back();
  };

  if (store.isLoading) {
    return (
      <View style={[styles.centerContainer, { backgroundColor: theme.colors.background }]}>
        <ActivityIndicator size="large" color={theme.colors.primary} />
      </View>
    );
  }

  return (
    <View style={[styles.root, { backgroundColor: theme.colors.background }]}>
      <PageHeader title={t('app_settings.title')} onBack={handleBack} />

      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={[styles.mainContainer, { maxWidth: formMaxWidth }]}>
          {/* Language Section */}
          <Animated.View entering={FadeInDown.springify().delay(0)}>
            <AppCard style={styles.card} mode="outlined">
              <AppCard.Content>
                <Text variant="titleMedium" style={styles.cardTitle}>
                  {t('app_settings.lang')}
                </Text>
                <AppSelect
                  value={language}
                  options={[
                    { label: '🇵🇱 Polski', value: 'pl' },
                    { label: '🇬🇧 English', value: 'en' },
                    { label: '🇩🇪 Deutsch', value: 'de' },
                    { label: '🇪🇸 Español', value: 'es' },
                    { label: '🇮🇹 Italiano', value: 'it' },
                  ]}
                  onChange={(v) => setLanguage(v as import('../i18n').LanguageCode)}
                  accessibilityLabel="Select app language"
                />
              </AppCard.Content>
            </AppCard>
          </Animated.View>

          {/* Theme Section */}
          <Animated.View entering={FadeInDown.springify().delay(80)}>
            <AppCard style={styles.card} mode="outlined">
              <AppCard.Content>
                <Text variant="titleMedium" style={styles.cardTitle}>
                  {t('app_settings.theme')}
                </Text>
                <SegmentedButtons
                  value={themePref}
                  onValueChange={(val) => setThemePref(val as ThemePref)}
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
              </AppCard.Content>
            </AppCard>
          </Animated.View>

          {/* Dynamic System Colors Section */}
          <Animated.View entering={FadeInDown.springify().delay(160)}>
            <AppCard style={styles.card} mode="outlined">
              <AppCard.Content>
                <Text variant="titleMedium" style={styles.cardTitle}>
                  {t('app_settings.dynamic_colors.title')}
                </Text>
                <Text
                  variant="bodyMedium"
                  style={{ color: theme.colors.onSurfaceVariant, marginBottom: 12 }}
                >
                  {t('app_settings.dynamic_colors.desc')}
                </Text>
                <SegmentedButtons
                  value={useSystemColors ? 'true' : 'false'}
                  onValueChange={(val) => setUseSystemColors(val === 'true')}
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
              </AppCard.Content>
            </AppCard>
          </Animated.View>

          {/* TTS Speed Rate Section */}
          <Animated.View entering={FadeInDown.springify().delay(240)}>
            <AppCard style={styles.card} mode="outlined">
              <AppCard.Content>
                <Text variant="titleMedium" style={styles.cardTitle}>
                  {t('app_settings.tts_rate')}
                </Text>
                <Text variant="bodyLarge" style={{ fontWeight: 'bold', marginBottom: 12 }}>
                  {ttsRate.toFixed(1)}x
                </Text>
                <View style={styles.sliderMockContainer}>
                  <SegmentedButtons
                    value={String(ttsRate)}
                    onValueChange={(val) => handleTtsRateChange(parseFloat(val))}
                    buttons={[
                      { value: '0.7', label: '0.7x' },
                      { value: '1', label: '1.0x' },
                      { value: '1.3', label: '1.3x' },
                      { value: '1.6', label: '1.6x' },
                    ]}
                  />
                </View>
              </AppCard.Content>
            </AppCard>
          </Animated.View>

          {/* Export / Import Backup Section */}
          <Animated.View entering={FadeInDown.springify().delay(320)}>
            <AppCard style={styles.card} mode="outlined">
              <AppCard.Content>
                <Text variant="titleMedium" style={styles.cardTitle}>
                  {t('app_settings.export_import')}
                </Text>
                <View style={styles.actionButtonsRow}>
                  <Button
                    mode="contained-tonal"
                    icon="share-variant"
                    onPress={handleExport}
                    style={styles.actionBtn}
                  >
                    {t('app_settings.export_btn')}
                  </Button>
                  <Button
                    mode="contained-tonal"
                    icon="import"
                    onPress={() => setImportVisible(true)}
                    style={styles.actionBtn}
                  >
                    {t('app_settings.import_btn')}
                  </Button>
                </View>
              </AppCard.Content>
            </AppCard>
          </Animated.View>

          {/* Reset / Danger Zone */}
          <Animated.View entering={FadeInDown.springify().delay(400)}>
            <AppCard style={[styles.card, { borderColor: theme.colors.error }]} mode="outlined">
              <AppCard.Content>
                <Text variant="titleMedium" style={[styles.cardTitle, { color: theme.colors.error }]}>
                  {t('app_settings.danger_zone')}
                </Text>
                <Text
                  variant="bodySmall"
                  style={{ color: theme.colors.onSurfaceVariant, marginBottom: 16 }}
                >
                  {t('app_settings.reset_confirm')}
                </Text>
                <Button
                  mode="contained"
                  buttonColor={theme.colors.error}
                  onPress={() => setResetVisible(true)}
                >
                  {t('app_settings.reset_btn')}
                </Button>
              </AppCard.Content>
            </AppCard>
          </Animated.View>
        </View>
      </ScrollView>

      {/* Portal Dialogs */}
      <Portal>
        {/* Import JSON Dialog */}
        <Dialog visible={importVisible} onDismiss={() => setImportVisible(false)}>
          <Dialog.Title>{t('app_settings.import_btn')}</Dialog.Title>
          <Dialog.Content>
            <TextInput
              mode="outlined"
              multiline
              numberOfLines={6}
              placeholder="Paste backup JSON string here..."
              value={importJson}
              onChangeText={setImportJson}
              style={styles.importTextArea}
              outlineStyle={{ borderRadius: TOKENS.radius.md }}
            />
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setImportVisible(false)}>{t('btn.cancel')}</Button>
            <Button mode="contained" onPress={handleImport} disabled={!importJson.trim()}>
              {t('btn.save')}
            </Button>
          </Dialog.Actions>
        </Dialog>

        {/* Reset Confirmation Dialog */}
        <Dialog visible={resetVisible} onDismiss={() => setResetVisible(false)}>
          <Dialog.Title>{t('app_settings.reset_btn')}</Dialog.Title>
          <Dialog.Content>
            <Text>{t('app_settings.reset_confirm')}</Text>
          </Dialog.Content>
          <Dialog.Actions>
            <Button onPress={() => setResetVisible(false)}>{t('btn.cancel')}</Button>
            <Button mode="contained" buttonColor={theme.colors.error} onPress={handleResetConfirm}>
              {t('app_settings.reset_btn')}
            </Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    paddingHorizontal: TOKENS.spacing.xxs,
    paddingTop: TOKENS.touchTarget.min,
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  scrollContent: {
    paddingBottom: TOKENS.spacing.xxl * 2,
    gap: TOKENS.spacing.lg,
  },
  mainContainer: {
    width: '100%',
    alignSelf: 'center',
    gap: TOKENS.spacing.lg,
    padding: TOKENS.spacing.xxs,
  },
  card: {
    borderRadius: TOKENS.radius.xl,
  },
  cardTitle: {
    fontWeight: 'bold',
    marginBottom: TOKENS.spacing.md,
  },
  segmentedButtons: {
    width: '100%',
  },
  sliderMockContainer: {
    marginTop: TOKENS.spacing.xs,
  },
  actionButtonsRow: {
    flexDirection: 'row',
    gap: TOKENS.spacing.md,
  },
  actionBtn: {
    flex: 1,
  },
  importTextArea: {
    minHeight: 120,
    fontSize: 12,
  },
});

import React, { useState, useEffect } from 'react';
import { View, StyleSheet, ScrollView, Platform, Share, Alert } from 'react-native';
import { Text, Button, Divider, useTheme, Card, SegmentedButtons, TextInput, Portal, Dialog, ActivityIndicator, Menu } from 'react-native-paper';
import Animated, { FadeInDown } from 'react-native-reanimated';
import { router } from 'expo-router';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useFlashcardStore } from '../hooks/useFlashcardStore';
import { useAppTheme, type ThemePref } from '../contexts/ThemeContext';
import { useI18n, type LanguageCode } from '../i18n';
import { PageHeader } from '../components/PageHeader';

export default function AppSettings() {
  const { t, language, setLanguage } = useI18n();
  const theme = useTheme();
  const store = useFlashcardStore();
  const { themePref, setThemePref, useSystemColors, setUseSystemColors } = useAppTheme();

  // TTS Rate state
  const [ttsRate, setTtsRate] = useState(1.0);
  const [langMenuVisible, setLangMenuVisible] = useState(false);
  const [importVisible, setImportVisible] = useState(false);
  const [importJson, setImportJson] = useState('');
  const [resetVisible, setResetVisible] = useState(false);

  // Load TTS rate on mount
  useEffect(() => {
    async function loadTtsRate() {
      try {
        const saved = await AsyncStorage.getItem('td-tts-rate');
        if (saved) {
          setTtsRate(parseFloat(saved));
        }
      } catch (err) {
        console.warn('Failed to load TTS rate:', err);
      }
    }
    loadTtsRate();
  }, []);

  const handleTtsRateChange = async (rate: number) => {
    const clampedRate = Math.max(0.5, Math.min(2.0, rate));
    setTtsRate(clampedRate);
    try {
      await AsyncStorage.setItem('td-tts-rate', String(clampedRate));
    } catch (err) {
      console.warn('Failed to save TTS rate:', err);
    }
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
    } catch (err) {
      if (Platform.OS === 'web') {
        alert('Invalid backup JSON!');
      } else {
        Alert.alert('Error', 'Invalid backup JSON!');
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
        {/* Language Section */}
        <Animated.View entering={FadeInDown.springify().delay(0)}>
        <Card style={styles.card} mode="outlined">
          <Card.Content>
            <Text variant="titleMedium" style={styles.cardTitle}>
              {t('app_settings.lang')}
            </Text>
            <Menu
              visible={langMenuVisible}
              onDismiss={() => setLangMenuVisible(false)}
              anchor={
                <Button mode="outlined" style={styles.dropdownAnchor} onPress={() => setLangMenuVisible(true)}>
                  {language === 'pl'
                    ? '🇵🇱 Polski'
                    : language === 'de'
                    ? '🇩🇪 Deutsch'
                    : language === 'es'
                    ? '🇪🇸 Español'
                    : language === 'it'
                    ? '🇮🇹 Italiano'
                    : '🇬🇧 English'}
                </Button>
              }
            >
              <Menu.Item onPress={() => { setLanguage('pl'); setLangMenuVisible(false); }} title="🇵🇱 Polski" />
              <Menu.Item onPress={() => { setLanguage('en'); setLangMenuVisible(false); }} title="🇬🇧 English" />
              <Menu.Item onPress={() => { setLanguage('de'); setLangMenuVisible(false); }} title="🇩🇪 Deutsch" />
              <Menu.Item onPress={() => { setLanguage('es'); setLangMenuVisible(false); }} title="🇪🇸 Español" />
              <Menu.Item onPress={() => { setLanguage('it'); setLangMenuVisible(false); }} title="🇮🇹 Italiano" />
            </Menu>
          </Card.Content>
        </Card>
        </Animated.View>

        {/* Theme Section */}
        <Animated.View entering={FadeInDown.springify().delay(80)}>
        <Card style={styles.card} mode="outlined">
          <Card.Content>
            <Text variant="titleMedium" style={styles.cardTitle}>
              {t('app_settings.theme')}
            </Text>
            <SegmentedButtons
              value={themePref}
              onValueChange={(val) => setThemePref(val as ThemePref)}
              buttons={[
                { value: 'light', label: t('app_settings.theme.light'), icon: 'weather-sunny' },
                { value: 'system', label: t('app_settings.theme.system'), icon: 'theme-light-dark' },
                { value: 'dark', label: t('app_settings.theme.dark'), icon: 'weather-night' },
              ]}
              style={styles.segmentedButtons}
            />
          </Card.Content>
        </Card>
        </Animated.View>

        {/* Dynamic System Colors Section */}
        <Animated.View entering={FadeInDown.springify().delay(160)}>
        <Card style={styles.card} mode="outlined">
          <Card.Content>
            <Text variant="titleMedium" style={styles.cardTitle}>
              {t('app_settings.dynamic_colors.title')}
            </Text>
            <Text variant="bodyMedium" style={{ color: theme.colors.onSurfaceVariant, marginBottom: 12 }}>
              {t('app_settings.dynamic_colors.desc')}
            </Text>
            <SegmentedButtons
              value={useSystemColors ? 'true' : 'false'}
              onValueChange={(val) => setUseSystemColors(val === 'true')}
              buttons={[
                { value: 'true', label: t('app_settings.dynamic_colors.enabled'), icon: 'palette' },
                { value: 'false', label: t('app_settings.dynamic_colors.disabled'), icon: 'palette-swatch-outline' },
              ]}
              style={styles.segmentedButtons}
            />
          </Card.Content>
        </Card>
        </Animated.View>

        {/* TTS Speed Rate Section */}
        <Animated.View entering={FadeInDown.springify().delay(240)}>
        <Card style={styles.card} mode="outlined">
          <Card.Content>
            <Text variant="titleMedium" style={styles.cardTitle}>
              {t('app_settings.tts_rate')}
            </Text>
            <Text variant="bodyLarge" style={{ fontWeight: 'bold', marginBottom: 12 }}>
              {ttsRate.toFixed(1)}x
            </Text>
            <View style={styles.sliderMockContainer}>
              {/* React Native Slider is deprecated in core, Segmented controls or simple buttons are cleaner */}
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
          </Card.Content>
        </Card>
        </Animated.View>

        {/* Export / Import Backup Section */}
        <Animated.View entering={FadeInDown.springify().delay(320)}>
        <Card style={styles.card} mode="outlined">
          <Card.Content>
            <Text variant="titleMedium" style={styles.cardTitle}>
              {t('app_settings.export_import')}
            </Text>
            <View style={styles.actionButtonsRow}>
              <Button mode="contained-tonal" icon="share-variant" onPress={handleExport} style={styles.actionBtn}>
                {t('app_settings.export_btn')}
              </Button>
              <Button mode="contained-tonal" icon="import" onPress={() => setImportVisible(true)} style={styles.actionBtn}>
                {t('app_settings.import_btn')}
              </Button>
            </View>
          </Card.Content>
        </Card>
        </Animated.View>

        {/* Reset / Danger Zone */}
        <Animated.View entering={FadeInDown.springify().delay(400)}>
        <Card style={[styles.card, { borderColor: theme.colors.error }]} mode="outlined">
          <Card.Content>
            <Text variant="titleMedium" style={[styles.cardTitle, { color: theme.colors.error }]}>
              {t('app_settings.danger_zone')}
            </Text>
            <Text variant="bodySmall" style={{ color: theme.colors.onSurfaceVariant, marginBottom: 16 }}>
              {t('app_settings.reset_confirm')}
            </Text>
            <Button mode="contained" buttonColor={theme.colors.error} onPress={() => setResetVisible(true)}>
              {t('app_settings.reset_btn')}
            </Button>
          </Card.Content>
        </Card>
        </Animated.View>
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
              outlineStyle={{ borderRadius: 12 }}
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
    paddingHorizontal: 16,
    paddingTop: 48,
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  scrollContent: {
    paddingBottom: 64,
    gap: 16,
  },
  card: {
    borderRadius: 20,
  },
  cardTitle: {
    fontWeight: 'bold',
    marginBottom: 12,
  },
  dropdownAnchor: {
    alignSelf: 'stretch',
  },
  segmentedButtons: {
    width: '100%',
  },
  sliderMockContainer: {
    marginTop: 4,
  },
  actionButtonsRow: {
    flexDirection: 'row',
    gap: 12,
  },
  actionBtn: {
    flex: 1,
  },
  importTextArea: {
    minHeight: 120,
    fontSize: 12,
  },
});

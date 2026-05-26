import React, { useState, useMemo } from 'react';
import { View, StyleSheet, ScrollView } from 'react-native';
import { TextInput, Button, useTheme, ActivityIndicator } from 'react-native-paper';
import Animated, { FadeInDown } from 'react-native-reanimated';
import { router } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useFlashcardStore } from '../hooks/useFlashcardStore';
import { PageHeader } from '../components/PageHeader';
import { useI18n } from '../i18n';
import {
  SEPARATORS,
  detectSeparator,
  detectPageCount,
  parseCSV,
  detectLangFromHeader,
} from '../import/importParser';

import { ImportSeparatorSelector } from '../components/import/ImportSeparatorSelector';
import { ImportPageConfig } from '../components/import/ImportPageConfig';
import { ImportPreviewTable } from '../components/import/ImportPreviewTable';

import { POPULAR_LANGS } from '../constants/languages';

export default function ImportPage() {
  const { t } = useI18n();
  const theme = useTheme();
  const store = useFlashcardStore();

  const [name, setName] = useState('');
  const [sepKey, setSepKey] = useState('semicolon');
  const [pageCount, setPageCount] = useState(2);
  const [pageNames, setPageNames] = useState(['Phrase', 'Tłumaczenie', '', '', '']);
  const [pageLangs, setPageLangs] = useState(['en-US', 'pl-PL', '', '', '']);
  const [rawText, setRawText] = useState('');

  const handleTextChange = (text: string) => {
    setRawText(text);
    if (!text.trim()) {
      return;
    }
    const detectedSep = detectSeparator(text);
    setSepKey(detectedSep);

    const sep = SEPARATORS[detectedSep as keyof typeof SEPARATORS] || ';';
    const detectedCount = detectPageCount(text, sep);
    setPageCount(detectedCount);

    const firstLine = text.split('\n')[0] || '';
    const parts = firstLine.split(sep);
    if (parts.length > 0) {
      setPageNames((prev) => {
        const nextNames = [...prev];
        parts.forEach((part, index) => {
          if (index < 5) {
            nextNames[index] = part.replace(/"/g, '').trim();
          }
        });
        return nextNames;
      });
      setPageLangs((prev) => {
        const nextLangs = [...prev];
        parts.forEach((part, index) => {
          if (index < 5) {
            const cleanPart = part.replace(/"/g, '').trim();
            nextLangs[index] = detectLangFromHeader(cleanPart);
          }
        });
        return nextLangs;
      });
    }
  };

  const rows = useMemo(() => {
    return parseCSV(rawText, sepKey, pageCount);
  }, [rawText, sepKey, pageCount]);

  const handleImport = () => {
    if (!name.trim()) return;
    const langs = pageLangs.slice(0, pageCount);
    const names = pageNames.slice(0, pageCount);
    const cardsData = rows.map((row) => ({
      pages: row,
    }));
    store.addGroupWithCards(name.trim(), langs, names, cardsData);
    router.back();
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
    <SafeAreaView
      style={[styles.root, { backgroundColor: theme.colors.background }]}
      edges={['top', 'left', 'right']}
    >
      <PageHeader title={t('import.title')} onBack={handleBack} />

      <ScrollView contentContainerStyle={styles.scrollContent}>
        <TextInput
          mode="outlined"
          label={t('dashboard.name_label')}
          value={name}
          onChangeText={setName}
          style={styles.input}
          outlineStyle={{ borderRadius: 12 }}
          accessibilityLabel="Import deck name input"
        />

        <TextInput
          mode="outlined"
          multiline
          numberOfLines={5}
          label={t('import.csv_label')}
          value={rawText}
          onChangeText={handleTextChange}
          placeholder={t('import.name_placeholder')}
          style={styles.textArea}
          outlineStyle={{ borderRadius: 12 }}
          accessibilityLabel="CSV TSV raw text input"
        />

        {/* Separator Select */}
        <ImportSeparatorSelector sepKey={sepKey} setSepKey={setSepKey} t={t} />

        {/* Page counter & Columns configuration */}
        <ImportPageConfig
          pageCount={pageCount}
          setPageCount={setPageCount}
          pageNames={pageNames}
          setPageNames={setPageNames}
          pageLangs={pageLangs}
          setPageLangs={setPageLangs}
          t={t}
          popularLangs={POPULAR_LANGS}
        />

        {/* Preview Section */}
        {rows.length > 0 && (
          <ImportPreviewTable rows={rows} pageCount={pageCount} pageNames={pageNames} t={t} />
        )}

        <Animated.View entering={FadeInDown.springify().delay(100)}>
          <Button
            mode="contained"
            onPress={handleImport}
            disabled={!name.trim() || rows.length === 0}
            style={styles.importBtn}
            accessibilityLabel="Perform flashcard import button"
          >
            {t('import.btn', { count: rows.length })}
          </Button>
        </Animated.View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  scrollContent: {
    paddingHorizontal: 16,
    paddingBottom: 64,
    gap: 16,
  },
  input: {
    height: 48,
  },
  textArea: {
    minHeight: 100,
  },
  importBtn: {
    marginTop: 16,
    borderRadius: 100,
  },
});

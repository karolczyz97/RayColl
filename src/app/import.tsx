import React, { useState, useMemo } from 'react';
import { View, StyleSheet, ScrollView } from 'react-native';
import { Text, TextInput, Button, useTheme, ActivityIndicator, Snackbar } from 'react-native-paper';
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
import { useResponsiveLayout } from '../hooks/useResponsiveLayout';
import { TOKENS } from '../theme/tokens';
import { AppCard } from '../components/AppCard';

export default function ImportPage() {
  const { t } = useI18n();
  const theme = useTheme();
  const store = useFlashcardStore();
  const { isCompact, isExpanded, contentMaxWidth, formMaxWidth } = useResponsiveLayout();

  const [name, setName] = useState('');
  const [sepKey, setSepKey] = useState('semicolon');
  const [pageCount, setPageCount] = useState(2);
  const [pageNames, setPageNames] = useState(['Phrase', 'Tłumaczenie', '', '', '']);
  const [pageLangs, setPageLangs] = useState(['en-US', 'pl-PL', '', '', '']);
  const [rawText, setRawText] = useState('');
  const [isImporting, setIsImporting] = useState(false);
  const [importError, setImportError] = useState('');

  const handleTextChange = (text: string) => {
    setImportError('');
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

  const handleImport = async () => {
    if (!name.trim()) return;
    setIsImporting(true);
    setImportError('');
    const langs = pageLangs.slice(0, pageCount);
    const names = pageNames.slice(0, pageCount);
    const cardsData = rows.map((row) => ({
      pages: row,
    }));
    try {
      const result = await store.importDeck({
        name,
        languages: langs,
        pageNames: names,
        cards: cardsData,
      });

      if (result.ok) {
        router.back();
        return;
      }

      setImportError(result.error);
    } finally {
      setIsImporting(false);
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

  const card1 = (
    <Animated.View entering={FadeInDown.springify().delay(0)} style={isExpanded ? { flex: 1 } : undefined}>
      <AppCard mode="outlined" style={styles.card}>
        <AppCard.Content style={styles.cardContent}>
          <TextInput
            mode="outlined"
            label={t('dashboard.name_label')}
            value={name}
            onChangeText={setName}
            style={styles.input}
            outlineStyle={{ borderRadius: TOKENS.radius.md }}
            accessibilityLabel="Import deck name input"
          />
          <View style={{ height: 4 }} />
          <TextInput
            mode="outlined"
            multiline
            numberOfLines={5}
            label={t('import.csv_label')}
            value={rawText}
            onChangeText={handleTextChange}
            placeholder={t('import.name_placeholder')}
            style={styles.textArea}
            outlineStyle={{ borderRadius: TOKENS.radius.md }}
            accessibilityLabel="CSV TSV raw text input"
          />
        </AppCard.Content>
      </AppCard>
    </Animated.View>
  );

  const card2 = (
    <Animated.View entering={FadeInDown.springify().delay(80)} style={isExpanded ? { flex: 1 } : undefined}>
      <AppCard mode="outlined" style={styles.card}>
        <AppCard.Content style={styles.cardContent}>
          <View style={styles.configHeaderRow}>
            <Text variant="titleMedium" style={styles.cardTitle}>
              {t('settings.pages_config')}
            </Text>
            <ImportSeparatorSelector sepKey={sepKey} setSepKey={setSepKey} t={t} />
          </View>
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
        </AppCard.Content>
      </AppCard>
    </Animated.View>
  );

  return (
    <SafeAreaView
      style={[styles.root, { backgroundColor: theme.colors.background }]}
      edges={['top', 'left', 'right']}
    >
      <PageHeader title={t('import.title')} onBack={handleBack} />

      <ScrollView contentContainerStyle={styles.scrollContent}>
        <View style={[styles.mainContainer, { maxWidth: contentMaxWidth }]}>
          {isExpanded ? (
            <View style={styles.row}>
              {card1}
              {card2}
            </View>
          ) : (
            <View style={styles.singleColumn}>
              {card1}
              {card2}
            </View>
          )}

          {/* Preview Section */}
          {rows.length > 0 && (
            <View style={[styles.previewContainer, !isCompact && { maxWidth: formMaxWidth, alignSelf: 'center' }]}>
              <ImportPreviewTable rows={rows} pageCount={pageCount} pageNames={pageNames} t={t} />
            </View>
          )}

          <Animated.View
            entering={FadeInDown.springify().delay(100)}
            style={[styles.importBtnWrapper, !isCompact && { maxWidth: formMaxWidth }]}
          >
            <Button
              mode="contained"
              onPress={handleImport}
              disabled={!name.trim() || rows.length === 0 || isImporting}
              loading={isImporting}
              style={styles.importBtn}
              accessibilityLabel="Perform flashcard import button"
            >
              {t('import.btn', { count: rows.length })}
            </Button>
          </Animated.View>
        </View>
      </ScrollView>

      <Snackbar
        visible={!!importError}
        onDismiss={() => setImportError('')}
        duration={6000}
        action={{ label: 'OK', onPress: () => setImportError('') }}
      >
        {importError}
      </Snackbar>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    paddingHorizontal: TOKENS.spacing.xxs,
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  scrollContent: {
    paddingHorizontal: TOKENS.spacing.xxs,
    paddingBottom: TOKENS.spacing.xxl * 2,
  },
  mainContainer: {
    width: '100%',
    alignSelf: 'center',
    gap: TOKENS.spacing.lg,
    padding: TOKENS.spacing.xxs,
  },
  row: {
    flexDirection: 'row',
    gap: TOKENS.spacing.xl,
    width: '100%',
    alignItems: 'flex-start',
  },
  singleColumn: {
    width: '100%',
    alignSelf: 'center',
    gap: TOKENS.spacing.lg,
  },
  previewContainer: {
    width: '100%',
  },
  importBtnWrapper: {
    width: '100%',
    alignSelf: 'center',
  },
  card: {
    borderRadius: TOKENS.radius.xl,
  },
  cardContent: {
    gap: TOKENS.spacing.md,
  },
  cardTitle: {
    fontWeight: 'bold',
  },
  configHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: TOKENS.spacing.xs,
  },
  input: {
    height: TOKENS.control.height,
  },
  textArea: {
    minHeight: 120,
  },
  importBtn: {
    borderRadius: TOKENS.radius.pill,
    height: TOKENS.control.height,
    justifyContent: 'center',
  },
});

import React, { useState, useMemo } from 'react';
import { View, StyleSheet, ScrollView, Platform } from 'react-native';
import {
  Text,
  TextInput,
  Button,
  useTheme,
  ActivityIndicator,
  Snackbar,
  Portal,
} from 'react-native-paper';
import Animated, { FadeInDown } from 'react-native-reanimated';
import { router } from 'expo-router';
import { SafeAreaView } from 'react-native-safe-area-context';
import * as DocumentPicker from 'expo-document-picker';
import { useFlashcardStore } from '../hooks/useFlashcardStore';
import { PageHeader } from '../components/PageHeader';
import { useI18n } from '../i18n';
import {
  SEPARATORS,
  detectSeparator,
  detectPageCount,
  parseCSV,
  detectLangFromHeader,
  serializeCSV,
} from '../import/importParser';
import { createNewSrsState } from '../srs/srsEngine';
import type { Flashcard, FlashcardGroup } from '../types/models';

import { ImportSeparatorSelector } from '../components/import/ImportSeparatorSelector';
import { ImportPageConfig } from '../components/import/ImportPageConfig';
import { FlashcardListItem } from '../components/browse/FlashcardListItem';
import { DeleteFlashcardDialog } from '../components/browse/DeleteFlashcardDialog';

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
  const [cards, setCards] = useState<Flashcard[]>([]);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editPages, setEditPages] = useState<string[]>([]);
  const [deleteCardId, setDeleteCardId] = useState<string | null>(null);

  const updateCardsFromText = (text: string, currentSepKey: string, currentPageCount: number) => {
    const parsedRows = parseCSV(text, currentSepKey, currentPageCount);
    setCards((prevCards) => {
      const isSame =
        prevCards.length === parsedRows.length &&
        prevCards.every((card, idx) => {
          const row = parsedRows[idx];
          return (
            card.pages.length === row.length &&
            card.pages.every((page, pIdx) => page === row[pIdx])
          );
        });

      if (isSame) {
        return prevCards;
      }

      return parsedRows.map((row, idx) => {
        const existingId =
          prevCards[idx]?.id || `import-${Math.random().toString(36).substring(2, 9)}`;
        return {
          id: existingId,
          pages: row,
          srsState: createNewSrsState(),
        };
      });
    });
  };

  const handleTextChange = (text: string) => {
    setImportError('');
    setRawText(text);
    if (!text.trim()) {
      setCards([]);
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

    updateCardsFromText(text, detectedSep, detectedCount);
  };

  const handleSepKeyChange = (newSep: string) => {
    setSepKey(newSep);
    updateCardsFromText(rawText, newSep, pageCount);
  };

  const handlePageCountChange: React.Dispatch<React.SetStateAction<number>> = (value) => {
    setPageCount((prev) => {
      const nextCount = typeof value === 'function' ? value(prev) : value;
      updateCardsFromText(rawText, sepKey, nextCount);
      return nextCount;
    });
  };

  const mockGroup = useMemo<FlashcardGroup>(() => {
    return {
      id: 'import-preview',
      name: name || 'Import Preview',
      cards: [],
      activeModeId: '',
      pageLanguages: pageLangs.slice(0, pageCount),
      pageNames: pageNames.slice(0, pageCount),
      activePageCount: pageCount,
    };
  }, [name, pageLangs, pageNames, pageCount]);

  const startEdit = (card: Flashcard) => {
    setEditingId(card.id);
    const pages = [...card.pages];
    while (pages.length < pageCount) {
      pages.push('');
    }
    setEditPages(pages);
  };

  const cancelEdit = () => {
    setEditingId(null);
    setEditPages([]);
  };

  const saveEdit = () => {
    if (!editingId) return;
    const updatedCards = cards.map((c) => {
      if (c.id === editingId) {
        return { ...c, pages: [...editPages] };
      }
      return c;
    });

    setCards(updatedCards);

    const newText = serializeCSV(
      updatedCards.map((c) => c.pages),
      sepKey
    );
    setRawText(newText);

    cancelEdit();
  };

  const confirmDeleteCard = () => {
    if (!deleteCardId) return;
    const updatedCards = cards.filter((c) => c.id !== deleteCardId);

    setCards(updatedCards);

    const newText = serializeCSV(
      updatedCards.map((c) => c.pages),
      sepKey
    );
    setRawText(newText);

    setDeleteCardId(null);
  };

  const handlePickFile = async () => {
    setImportError('');
    try {
      const result = await DocumentPicker.getDocumentAsync({
        type: ['text/csv', 'text/plain', 'text/comma-separated-values', 'text/tab-separated-values'],
        copyToCacheDirectory: true,
      });

      if (!result.canceled && result.assets && result.assets.length > 0) {
        const asset = result.assets[0];
        let fileContent = '';
        if (Platform.OS === 'web' && asset.file) {
          fileContent = await asset.file.text();
        } else {
          const response = await fetch(asset.uri);
          fileContent = await response.text();
        }
        if (fileContent) {
          handleTextChange(fileContent);
        }
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to pick or read file';
      setImportError(msg);
    }
  };

  const handleImport = async () => {
    if (!name.trim()) return;
    setIsImporting(true);
    setImportError('');
    const langs = pageLangs.slice(0, pageCount);
    const names = pageNames.slice(0, pageCount);
    const cardsData = cards.map((c) => ({
      pages: c.pages,
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
          <Button
            mode="outlined"
            icon="upload"
            onPress={handlePickFile}
            style={styles.uploadBtn}
            accessibilityLabel="Upload CSV, TXT, or MD file"
          >
            {t('import.upload_file') === 'import.upload_file'
              ? 'Upload file (.csv, .txt, .md)'
              : t('import.upload_file')}
          </Button>
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
            <ImportSeparatorSelector sepKey={sepKey} setSepKey={handleSepKeyChange} t={t} />
          </View>
          <ImportPageConfig
            pageCount={pageCount}
            setPageCount={handlePageCountChange}
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
            <View style={[styles.singleColumn]}>
              {card1}
              {card2}
            </View>
          )}

          {/* Preview Section */}
          {cards.length > 0 && (
            <Animated.View
              entering={FadeInDown.springify().delay(160)}
              style={styles.previewContainer}
            >
              <AppCard mode="outlined" style={styles.card}>
                <AppCard.Content style={styles.cardContent}>
                  <Text variant="titleMedium" style={styles.cardTitle}>
                    {`${t('import.preview') || 'Preview'} (${cards.length})`}
                  </Text>
                  <View style={styles.previewList}>
                    {cards.map((card) => (
                      <FlashcardListItem
                        key={card.id}
                        card={card}
                        group={mockGroup}
                        isEditing={editingId === card.id}
                        editPages={editPages}
                        setEditPages={setEditPages}
                        onSave={saveEdit}
                        onCancel={cancelEdit}
                        onStartEdit={() => startEdit(card)}
                        onDelete={() => setDeleteCardId(card.id)}
                        t={t}
                      />
                    ))}
                  </View>
                </AppCard.Content>
              </AppCard>
            </Animated.View>
          )}

          <Animated.View
            entering={FadeInDown.springify().delay(100)}
            style={[styles.importBtnWrapper, !isCompact && { maxWidth: formMaxWidth }]}
          >
            <Button
              mode="contained"
              onPress={handleImport}
              disabled={!name.trim() || cards.length === 0 || isImporting}
              loading={isImporting}
              style={styles.importBtn}
              accessibilityLabel="Perform flashcard import button"
            >
              {t('import.btn', { count: cards.length })}
            </Button>
          </Animated.View>
        </View>
      </ScrollView>

      <Portal>
        <DeleteFlashcardDialog
          visible={!!deleteCardId}
          onDismiss={() => setDeleteCardId(null)}
          onConfirm={confirmDeleteCard}
          t={t}
        />
      </Portal>

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
  uploadBtn: {
    marginTop: TOKENS.spacing.xs,
    borderRadius: TOKENS.radius.pill,
  },
  previewList: {
    gap: TOKENS.spacing.md,
    marginTop: TOKENS.spacing.xs,
  },
  importBtn: {
    borderRadius: TOKENS.radius.pill,
    height: TOKENS.control.height,
    justifyContent: 'center',
  },
});

import React, { useState, useMemo, useEffect } from 'react';
import { View, StyleSheet, ScrollView } from 'react-native';
import { Text, TextInput, Button, IconButton, useTheme, Card, Menu, ActivityIndicator } from 'react-native-paper';
import { router } from 'expo-router';
import { useFlashcardStore } from '../hooks/useFlashcardStore';
import { PageHeader } from '../components/PageHeader';
import { useI18n } from '../i18n';

const SEPARATORS: Record<string, string> = { tab: '\t', semicolon: ';', comma: ',', pipe: '|' };

const POPULAR_LANGS = [
  { code: 'pl-PL', label: 'Polski' }, { code: 'en-US', label: 'Angielski' },
  { code: 'es-ES', label: 'Hiszpański' }, { code: 'de-DE', label: 'Niemiecki' },
  { code: 'fr-FR', label: 'Francuski' }, { code: 'it-IT', label: 'Włoski' },
  { code: 'pt-PT', label: 'Portugalski' }, { code: 'ru-RU', label: 'Rosyjski' },
  { code: 'ja-JP', label: 'Japoński' }, { code: 'zh-CN', label: 'Chiński' },
];

function detectSeparator(text: string): string {
  const firstLine = text.split('\n').find(l => l.trim()) || '';
  const counts: Record<string, number> = {};
  for (const [key, sep] of Object.entries(SEPARATORS)) {
    counts[key] = firstLine.split(sep).length - 1;
  }
  let best = 'semicolon';
  for (const [key, count] of Object.entries(counts)) {
    if (count > (counts[best] || 0)) best = key;
  }
  return counts[best] > 0 ? best : 'semicolon';
}

function detectPageCount(text: string, sep: string): number {
  const lines = text.split('\n').filter(l => l.trim());
  if (lines.length === 0) return 2;
  const counts = lines.map(l => l.split(sep).length);
  const maxCols = Math.max(...counts);
  return Math.max(2, Math.min(5, maxCols));
}

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
  const [autoDetected, setAutoDetected] = useState(false);

  // Dropdown states
  const [sepMenuVisible, setSepMenuVisible] = useState(false);
  const [langMenuIndex, setLangMenuIndex] = useState<number | null>(null);

  useEffect(() => {
    if (!rawText.trim()) {
      setAutoDetected(false);
      return;
    }
    if (autoDetected) return;
    const detectedSep = detectSeparator(rawText);
    setSepKey(detectedSep);
    const sep = SEPARATORS[detectedSep] || ';';
    const detectedCount = detectPageCount(rawText, sep);
    setPageCount(detectedCount);
    setAutoDetected(true);
  }, [rawText, autoDetected]);

  const handleTextChange = (text: string) => {
    setAutoDetected(false);
    setRawText(text);
  };

  const rows = useMemo(() => {
    const sep = SEPARATORS[sepKey] || ';';
    return rawText
      .split('\n')
      .filter(l => l.trim())
      .map(line => {
        const parts = line.split(sep).map(s => s.trim());
        while (parts.length < pageCount) parts.push('');
        return parts.slice(0, pageCount);
      });
  }, [rawText, sepKey, pageCount]);

  const handleImport = () => {
    if (!name.trim()) return;
    const langs = pageLangs.slice(0, pageCount);
    const names = pageNames.slice(0, pageCount);
    const groupId = store.addGroup(name.trim(), langs, names);
    rows.forEach(row => store.addFlashcard(groupId, row));
    router.back();
  };

  const updatePageName = (i: number, v: string) => {
    const n = [...pageNames];
    n[i] = v;
    setPageNames(n);
  };

  const updatePageLang = (i: number, v: string) => {
    const n = [...pageLangs];
    n[i] = v;
    setPageLangs(n);
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
      <PageHeader title={t('import.title')} onBack={handleBack} />

      <ScrollView contentContainerStyle={styles.scrollContent}>
        <TextInput
          mode="outlined"
          label={t('dashboard.name_label')}
          value={name}
          onChangeText={setName}
          style={styles.input}
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
        />

        {/* Separator Select */}
        <Menu
          visible={sepMenuVisible}
          onDismiss={() => setSepMenuVisible(false)}
          anchor={
            <Button mode="outlined" style={styles.menuAnchor} onPress={() => setSepMenuVisible(true)}>
              {`${t('import.separator')}: ${t(`import.sep.${sepKey}`)}`}
            </Button>
          }
        >
          <Menu.Item onPress={() => { setSepKey('tab'); setSepMenuVisible(false); }} title={t('import.sep.tab')} />
          <Menu.Item onPress={() => { setSepKey('semicolon'); setSepMenuVisible(false); }} title={t('import.sep.semicolon')} />
          <Menu.Item onPress={() => { setSepKey('comma'); setSepMenuVisible(false); }} title={t('import.sep.comma')} />
          <Menu.Item onPress={() => { setSepKey('pipe'); setSepMenuVisible(false); }} title={t('import.sep.pipe')} />
        </Menu>

        {/* Page counter adjustment */}
        <View style={styles.counterRow}>
          <Text>{t('import.pages_count')}</Text>
          <View style={styles.counterButtons}>
            <IconButton
              icon="minus-box"
              size={28}
              onPress={() => setPageCount(p => Math.max(2, p - 1))}
              disabled={pageCount <= 2}
            />
            <Text style={styles.counterText}>{pageCount}</Text>
            <IconButton
              icon="plus-box"
              size={28}
              onPress={() => setPageCount(p => Math.min(5, p + 1))}
              disabled={pageCount >= 5}
            />
          </View>
        </View>

        {/* Columns Settings */}
        <View style={styles.columnsSection}>
          {Array.from({ length: pageCount }).map((_, i) => (
            <View key={i} style={styles.columnRow}>
              <TextInput
                mode="outlined"
                label={t('import.page_name_label', { index: i + 1 })}
                value={pageNames[i]}
                onChangeText={v => updatePageName(i, v)}
                style={styles.columnNameInput}
              />
              <Menu
                visible={langMenuIndex === i}
                onDismiss={() => setLangMenuIndex(null)}
                anchor={
                  <Button
                    mode="outlined"
                    compact
                    style={styles.langBtn}
                    onPress={() => setLangMenuIndex(i)}
                  >
                    {pageLangs[i] ? t(`lang.${pageLangs[i]}`) : t('import.lang_label')}
                  </Button>
                }
              >
                {POPULAR_LANGS.map(l => (
                  <Menu.Item
                    key={l.code}
                    onPress={() => {
                      updatePageLang(i, l.code);
                      setLangMenuIndex(null);
                    }}
                    title={t(`lang.${l.code}`)}
                  />
                ))}
              </Menu>
            </View>
          ))}
        </View>

        {/* Preview Section */}
        {rows.length > 0 && (
          <View style={styles.previewSection}>
            <Text variant="titleMedium" style={styles.previewTitle}>
              {`${t('import.preview')} (${rows.length})`}
            </Text>
            <ScrollView horizontal style={styles.horizontalScroll}>
              <View style={styles.table}>
                {/* Header row */}
                <View style={[styles.tableRow, styles.tableHeader, { backgroundColor: theme.colors.surfaceVariant }]}>
                  {pageNames.slice(0, pageCount).map((n, i) => (
                    <Text key={i} style={styles.tableHeaderCell}>
                      {n || t('import.page_label', { index: i + 1 })}
                    </Text>
                  ))}
                </View>
                {/* Data rows */}
                {rows.slice(0, 30).map((row, ri) => (
                  <View key={ri} style={[styles.tableRow, { borderBottomColor: theme.colors.outlineVariant }]}>
                    {row.map((cell, ci) => (
                      <Text key={ci} style={styles.tableCell}>
                        {cell}
                      </Text>
                    ))}
                  </View>
                ))}
              </View>
            </ScrollView>
          </View>
        )}

        <Button
          mode="contained"
          onPress={handleImport}
          disabled={!name.trim() || rows.length === 0}
          style={styles.importBtn}
        >
          {t('import.btn', { count: rows.length })}
        </Button>
      </ScrollView>
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
  input: {
    height: 48,
  },
  textArea: {
    minHeight: 100,
  },
  menuAnchor: {
    borderRadius: 8,
  },
  counterRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 8,
  },
  counterButtons: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  counterText: {
    fontSize: 18,
    fontWeight: 'bold',
    minWidth: 24,
    textAlign: 'center',
  },
  columnsSection: {
    gap: 12,
  },
  columnRow: {
    flexDirection: 'row',
    gap: 8,
    alignItems: 'center',
  },
  columnNameInput: {
    flex: 1,
    height: 40,
  },
  langBtn: {
    minWidth: 120,
    height: 40,
    justifyContent: 'center',
  },
  previewSection: {
    marginTop: 16,
  },
  previewTitle: {
    fontWeight: 'bold',
    marginBottom: 8,
  },
  horizontalScroll: {
    borderRadius: 8,
    borderWidth: 1,
    borderColor: '#e0e0e0',
  },
  table: {
    flexDirection: 'column',
    minWidth: 320,
  },
  tableRow: {
    flexDirection: 'row',
    paddingVertical: 8,
    paddingHorizontal: 12,
  },
  tableHeader: {
    borderTopLeftRadius: 8,
    borderTopRightRadius: 8,
  },
  tableHeaderCell: {
    flex: 1,
    fontWeight: 'bold',
    minWidth: 100,
    fontSize: 12,
  },
  tableCell: {
    flex: 1,
    minWidth: 100,
    fontSize: 12,
  },
  importBtn: {
    marginTop: 16,
    borderRadius: 100,
  },
});

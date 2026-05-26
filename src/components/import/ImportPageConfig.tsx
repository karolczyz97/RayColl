import React, { useState } from 'react';
import { View, StyleSheet } from 'react-native';
import { Text, IconButton, TextInput, Menu, Button } from 'react-native-paper';

interface Props {
  pageCount: number;
  setPageCount: React.Dispatch<React.SetStateAction<number>>;
  pageNames: string[];
  setPageNames: React.Dispatch<React.SetStateAction<string[]>>;
  pageLangs: string[];
  setPageLangs: React.Dispatch<React.SetStateAction<string[]>>;
  t: (key: string, replacements?: any) => string;
  popularLangs: { code: string; label: string }[];
}

export function ImportPageConfig({
  pageCount,
  setPageCount,
  pageNames,
  setPageNames,
  pageLangs,
  setPageLangs,
  t,
  popularLangs,
}: Props) {
  const [langMenuIndex, setLangMenuIndex] = useState<number | null>(null);

  const updatePageName = (i: number, v: string) => {
    setPageNames((prev) => {
      const next = [...prev];
      next[i] = v;
      return next;
    });
  };

  const updatePageLang = (i: number, v: string) => {
    setPageLangs((prev) => {
      const next = [...prev];
      next[i] = v;
      return next;
    });
  };

  return (
    <View style={styles.container}>
      {/* Page counter adjustment */}
      <View style={styles.counterRow}>
        <Text>{t('import.pages_count')}</Text>
        <View style={styles.counterButtons}>
          <IconButton
            icon="minus-box"
            size={28}
            onPress={() => setPageCount((p) => Math.max(2, p - 1))}
            disabled={pageCount <= 2}
            accessibilityLabel="Decrease import page count"
          />
          <Text style={styles.counterText}>{pageCount}</Text>
          <IconButton
            icon="plus-box"
            size={28}
            onPress={() => setPageCount((p) => Math.min(5, p + 1))}
            disabled={pageCount >= 5}
            accessibilityLabel="Increase import page count"
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
              onChangeText={(v) => updatePageName(i, v)}
              style={styles.columnNameInput}
              outlineStyle={{ borderRadius: 12 }}
              accessibilityLabel={`Edit page ${i + 1} header name`}
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
                  accessibilityLabel={`Select language for page ${i + 1}`}
                >
                  {pageLangs[i] ? t(`lang.${pageLangs[i]}`) : t('import.lang_label')}
                </Button>
              }
            >
              {popularLangs.map((l) => (
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
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 8,
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
});

import React, { useState } from 'react';
import { View, StyleSheet } from 'react-native';
import { Text, IconButton, TextInput, Menu, Button } from 'react-native-paper';
import type { TranslationFn } from '../../i18n';
import { TOKENS } from '../../theme/tokens';

const LANGUAGE_SELECT_WIDTH = 140;

interface Props {
  pageCount: number;
  visiblePageNames: string[];
  visiblePageLanguages: string[];
  adjustPageCount: (count: number) => void;
  movePageSetting: (i: number, dir: -1 | 1) => void;
  setColNames: React.Dispatch<React.SetStateAction<string[]>>;
  handleColBlur: (i: number) => void;
  updatePageLangValue: (i: number, v: string) => void;
  t: TranslationFn;
  popularLangs: { code: string; label: string }[];
}

export function PagesConfigSection({
  pageCount,
  visiblePageNames,
  visiblePageLanguages,
  adjustPageCount,
  movePageSetting,
  setColNames,
  handleColBlur,
  updatePageLangValue,
  t,
  popularLangs,
}: Props) {
  const [langMenuIndex, setLangMenuIndex] = useState<number | null>(null);

  return (
    <View style={styles.container}>
      <Text variant="titleMedium" style={styles.sectionTitle}>
        {t('settings.pages_config')}
      </Text>
      <View style={styles.counterRow}>
        <Text>{t('import.pages_count')}</Text>
        <View style={styles.counterButtons}>
          <IconButton
            icon="minus-box"
            size={28}
            style={styles.counterBtn}
            onPress={() => adjustPageCount(pageCount - 1)}
            disabled={pageCount <= 2}
            accessibilityLabel="Decrease visible pages count"
          />
          <Text style={styles.counterText}>{pageCount}</Text>
          <IconButton
            icon="plus-box"
            size={28}
            style={styles.counterBtn}
            onPress={() => adjustPageCount(pageCount + 1)}
            disabled={pageCount >= 5}
            accessibilityLabel="Increase visible pages count"
          />
        </View>
      </View>

      {visiblePageNames.map((pn, i) => (
        <View key={i} style={styles.columnRow}>
          <View style={styles.sortButtons}>
            <IconButton
              icon="arrow-up"
              size={16}
              style={styles.sortBtn}
              onPress={() => movePageSetting(i, -1)}
              disabled={i === 0}
              accessibilityLabel={`Move page ${i + 1} up`}
            />
            <IconButton
              icon="arrow-down"
              size={16}
              style={styles.sortBtn}
              onPress={() => movePageSetting(i, 1)}
              disabled={i === pageCount - 1}
              accessibilityLabel={`Move page ${i + 1} down`}
            />
          </View>
          <TextInput
            mode="outlined"
            label={t('import.page_label', { index: i + 1 })}
            value={pn}
            onChangeText={(v) => {
              setColNames((prev) => {
                const next = [...prev];
                next[i] = v;
                return next;
              });
            }}
            onBlur={() => handleColBlur(i)}
            style={styles.pageNameInput}
            outlineStyle={{ borderRadius: 12 }}
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
                {visiblePageLanguages[i]
                  ? t(`lang.${visiblePageLanguages[i]}`)
                  : t('import.lang_label')}
              </Button>
            }
          >
            {popularLangs.map((l) => (
              <Menu.Item
                key={l.code}
                onPress={() => {
                  updatePageLangValue(i, l.code);
                  setLangMenuIndex(null);
                }}
                title={t(`lang.${l.code}`)}
              />
            ))}
          </Menu>
        </View>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 8,
  },
  sectionTitle: {
    fontWeight: 'bold',
    marginBottom: 4,
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
    width: LANGUAGE_SELECT_WIDTH,
    justifyContent: 'center',
  },
  counterText: {
    fontSize: 18,
    fontWeight: 'bold',
    minWidth: TOKENS.touchTarget.compact,
    textAlign: 'center',
  },
  counterBtn: {
    margin: 0,
  },
  columnRow: {
    flexDirection: 'row',
    gap: 8,
    alignItems: 'center',
    marginBottom: 8,
  },
  sortButtons: {
    flexDirection: 'column',
    alignItems: 'center',
  },
  sortBtn: {
    margin: 0,
    height: 20,
    width: 20,
  },
  pageNameInput: {
    flex: 1,
    height: 40,
  },
  langBtn: {
    width: LANGUAGE_SELECT_WIDTH,
    height: 40,
    justifyContent: 'center',
  },
});

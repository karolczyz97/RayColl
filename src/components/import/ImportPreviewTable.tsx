import React from 'react';
import { View, StyleSheet, ScrollView } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
import Animated, { FadeIn } from 'react-native-reanimated';
import type { TranslationFn } from '../../i18n';

interface Props {
  rows: string[][];
  pageCount: number;
  pageNames: string[];
  t: TranslationFn;
}

export function ImportPreviewTable({ rows, pageCount, pageNames, t }: Props) {
  const theme = useTheme();

  return (
    <Animated.View entering={FadeIn.springify()} style={styles.previewSection}>
      <Text variant="titleMedium" style={styles.previewTitle}>
        {`${t('import.preview')} (${rows.length})`}
      </Text>
      <ScrollView
        horizontal
        style={[styles.horizontalScroll, { borderColor: theme.colors.outlineVariant }]}
      >
        <View style={styles.table}>
          {/* Header row */}
          <View
            style={[
              styles.tableRow,
              styles.tableHeader,
              { backgroundColor: theme.colors.surfaceVariant },
            ]}
          >
            {pageNames.slice(0, pageCount).map((n, i) => (
              <Text key={i} style={styles.tableHeaderCell}>
                {n || t('import.page_label', { index: i + 1 })}
              </Text>
            ))}
          </View>
          {/* Data rows */}
          {rows.slice(0, 30).map((row, ri) => (
            <View
              key={ri}
              style={[styles.tableRow, { borderBottomColor: theme.colors.outlineVariant }]}
            >
              {row.map((cell, ci) => (
                <Text key={ci} style={styles.tableCell}>
                  {cell}
                </Text>
              ))}
            </View>
          ))}
        </View>
      </ScrollView>
    </Animated.View>
  );
}

const styles = StyleSheet.create({
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
});

import React, { useMemo } from 'react';
import { ScrollView, StyleSheet, View } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
import type { TranslationFn } from '../../i18n';
import { getLocalDateString } from '../../store/selectors/stats';
import { getHeatmapColor } from '../../theme/semanticColors';
import { TOKENS } from '../../theme/tokens';

interface HeatmapGridProps {
  heatmap: Record<string, number>;
  t: TranslationFn;
}

export function HeatmapGrid({ heatmap, t }: HeatmapGridProps) {
  const theme = useTheme();

  const columnsData = useMemo(() => {
    const columns = 20;
    const rows = 7;
    const today = new Date();
    const data: { date: string; count: number }[][] = [];

    for (let column = columns - 1; column >= 0; column -= 1) {
      const columnCells: { date: string; count: number }[] = [];

      for (let row = 0; row < rows; row += 1) {
        const daysBack = column * 7 + (6 - row);
        const date = new Date(today);
        date.setDate(date.getDate() - daysBack);
        const key = getLocalDateString(date);
        columnCells.push({ date: key, count: heatmap[key] || 0 });
      }

      data.push(columnCells);
    }

    return data;
  }, [heatmap]);

  const dayLabels = [
    t('stats.day.mon'),
    '',
    t('stats.day.wed'),
    '',
    t('stats.day.fri'),
    '',
    t('stats.day.sun'),
  ];

  return (
    <View style={styles.heatmapContainer}>
      <View style={styles.gridWrapper}>
        <View style={styles.dayLabelsColumn}>
          {dayLabels.map((label, index) => (
            <View key={`${label}-${index}`} style={styles.dayLabelCell}>
              {label ? (
                <Text style={[styles.dayLabelText, { color: theme.colors.outline }]}>{label}</Text>
              ) : null}
            </View>
          ))}
        </View>

        <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.gridScroll}>
          <View style={styles.gridContainer}>
            {columnsData.map((column, columnIndex) => (
              <View key={columnIndex} style={styles.gridColumn}>
                {column.map((cell, rowIndex) => (
                  <View
                    key={`${cell.date}-${rowIndex}`}
                    style={[
                      styles.gridCell,
                      { backgroundColor: getHeatmapColor(theme, cell.count) },
                    ]}
                  />
                ))}
              </View>
            ))}
          </View>
        </ScrollView>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  heatmapContainer: {
    width: '100%',
    alignItems: 'center',
  },
  gridWrapper: {
    position: 'relative',
    alignSelf: 'center',
    width: '100%',
    maxWidth: 337,
  },
  dayLabelsColumn: {
    position: 'absolute',
    left: -28,
    width: 24,
    justifyContent: 'space-around',
    height: 116,
  },
  dayLabelCell: {
    height: 14,
    justifyContent: 'center',
  },
  dayLabelText: {
    fontSize: 9,
  },
  gridScroll: {
    paddingBottom: TOKENS.spacing.xs,
  },
  gridContainer: {
    flexDirection: 'row',
    gap: TOKENS.spacing.xxs,
    height: 116,
  },
  gridColumn: {
    flexDirection: 'column',
    gap: TOKENS.spacing.xxs,
  },
  gridCell: {
    width: 14,
    height: 14,
    borderRadius: 3,
  },
});

import React from 'react';
import { StyleSheet, ScrollView } from 'react-native';
import { useTheme, Chip } from 'react-native-paper';
import { getReviewStatusColor } from '../../theme/semanticColors';
import type { TranslationFn } from '../../i18n';

interface Props {
  browseFilter: 'all' | 'learning' | 'review' | 'new' | 'mastered';
  setBrowseFilter: (filter: 'all' | 'learning' | 'review' | 'new' | 'mastered') => void;
  stats: { total: number; learning: number; review: number; newCount: number; mastered: number };
  t: TranslationFn;
}

export function BrowseFilterChips({ browseFilter, setBrowseFilter, stats, t }: Props) {
  const theme = useTheme();

  const newColors = getReviewStatusColor(theme, 'new');
  const learningColors = getReviewStatusColor(theme, 'learning');
  const reviewColors = getReviewStatusColor(theme, 'review');
  const masteredColors = getReviewStatusColor(theme, 'mastered');

  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={styles.filterChipsRow}
    >
      <Chip
        selected={browseFilter === 'all'}
        showSelectedCheck={false}
        onPress={() => setBrowseFilter('all')}
        style={styles.chip}
        accessibilityLabel="Filter all cards"
      >
        {`${t('filter.all')} (${stats.total})`}
      </Chip>
      <Chip
        selected={browseFilter === 'learning'}
        showSelectedCheck={false}
        onPress={() => setBrowseFilter('learning')}
        style={[
          styles.chip,
          browseFilter === 'learning' && { backgroundColor: learningColors.bg },
        ]}
        selectedColor={
          browseFilter === 'learning' ? learningColors.color : undefined
        }
        accessibilityLabel="Filter learning cards"
      >
        {`${t('filter.learning')} (${stats.learning})`}
      </Chip>
      <Chip
        selected={browseFilter === 'review'}
        showSelectedCheck={false}
        onPress={() => setBrowseFilter('review')}
        style={[
          styles.chip,
          browseFilter === 'review' && { backgroundColor: reviewColors.bg },
        ]}
        selectedColor={browseFilter === 'review' ? reviewColors.color : undefined}
        accessibilityLabel="Filter review cards"
      >
        {`${t('filter.review')} (${stats.review})`}
      </Chip>
      <Chip
        selected={browseFilter === 'new'}
        showSelectedCheck={false}
        onPress={() => setBrowseFilter('new')}
        style={[
          styles.chip,
          browseFilter === 'new' && { backgroundColor: newColors.bg },
        ]}
        selectedColor={browseFilter === 'new' ? newColors.color : undefined}
        accessibilityLabel="Filter new cards"
      >
        {`${t('filter.new')} (${stats.newCount})`}
      </Chip>
      <Chip
        selected={browseFilter === 'mastered'}
        showSelectedCheck={false}
        onPress={() => setBrowseFilter('mastered')}
        style={[
          styles.chip,
          browseFilter === 'mastered' && { backgroundColor: masteredColors.bg },
        ]}
        selectedColor={
          browseFilter === 'mastered' ? masteredColors.color : undefined
        }
        accessibilityLabel="Filter mastered cards"
      >
        {`${t('filter.mastered')} (${stats.mastered})`}
      </Chip>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  filterChipsRow: {
    flexDirection: 'row',
    gap: 8,
    height: 40,
    marginBottom: 16,
  },
  chip: {
    marginRight: 4,
    height: 32,
  },
});

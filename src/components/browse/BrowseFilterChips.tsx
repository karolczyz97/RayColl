import React from 'react';
import { StyleSheet, ScrollView } from 'react-native';
import { Chip } from 'react-native-paper';
import { SRS_CATEGORIES_TOKENS } from '../../theme/srsTokens';

interface Props {
  browseFilter: 'all' | 'learning' | 'review' | 'new' | 'mastered';
  setBrowseFilter: (filter: 'all' | 'learning' | 'review' | 'new' | 'mastered') => void;
  stats: { total: number; learning: number; review: number; newCount: number; mastered: number };
  t: (key: string, replacements?: any) => string;
}

export function BrowseFilterChips({ browseFilter, setBrowseFilter, stats, t }: Props) {
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
        style={[styles.chip, browseFilter === 'learning' && { backgroundColor: SRS_CATEGORIES_TOKENS.learning.bg }]}
        selectedColor={browseFilter === 'learning' ? SRS_CATEGORIES_TOKENS.learning.color : undefined}
        accessibilityLabel="Filter learning cards"
      >
        {`${t('filter.learning')} (${stats.learning})`}
      </Chip>
      <Chip
        selected={browseFilter === 'review'}
        showSelectedCheck={false}
        onPress={() => setBrowseFilter('review')}
        style={[styles.chip, browseFilter === 'review' && { backgroundColor: SRS_CATEGORIES_TOKENS.review.bg }]}
        selectedColor={browseFilter === 'review' ? SRS_CATEGORIES_TOKENS.review.color : undefined}
        accessibilityLabel="Filter review cards"
      >
        {`${t('filter.review')} (${stats.review})`}
      </Chip>
      <Chip
        selected={browseFilter === 'new'}
        showSelectedCheck={false}
        onPress={() => setBrowseFilter('new')}
        style={[styles.chip, browseFilter === 'new' && { backgroundColor: SRS_CATEGORIES_TOKENS.new.bg }]}
        selectedColor={browseFilter === 'new' ? SRS_CATEGORIES_TOKENS.new.color : undefined}
        accessibilityLabel="Filter new cards"
      >
        {`${t('filter.new')} (${stats.newCount})`}
      </Chip>
      <Chip
        selected={browseFilter === 'mastered'}
        showSelectedCheck={false}
        onPress={() => setBrowseFilter('mastered')}
        style={[styles.chip, browseFilter === 'mastered' && { backgroundColor: SRS_CATEGORIES_TOKENS.mastered.bg }]}
        selectedColor={browseFilter === 'mastered' ? SRS_CATEGORIES_TOKENS.mastered.color : undefined}
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

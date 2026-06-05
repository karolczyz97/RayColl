import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Text } from 'react-native-paper';
import { AppSelect } from '@/components/AppSelect';
import { CARD_FILTER_OPTIONS, type CardFilter } from '@/constants/cardFilters';
import { TOKENS } from '@/theme/tokens';
import { useI18n } from '@/i18n';

interface StudyScopeSectionProps {
  studyFilter: CardFilter;
  onFilterChange: (filter: CardFilter) => void;
}

function isCardFilter(value: string): value is CardFilter {
  return CARD_FILTER_OPTIONS.some((filter) => filter.value === value);
}

export function StudyScopeSection({ studyFilter, onFilterChange }: StudyScopeSectionProps) {
  const { t } = useI18n();
  const options = CARD_FILTER_OPTIONS.map((filter) => ({
    label: t(filter.labelKey),
    value: filter.value,
  }));

  return (
    <View style={styles.container}>
      <Text variant="titleMedium" style={styles.sectionTitle}>
        {t('settings.study_scope')}
      </Text>
      <AppSelect
        value={studyFilter}
        options={options}
        onChange={(value) => {
          if (isCardFilter(value)) {
            onFilterChange(value);
          }
        }}
        accessibilityLabel="Study scope filter selection"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: TOKENS.spacing.sm,
    width: '100%',
  },
  sectionTitle: {
    fontWeight: TOKENS.typography.weight.bold,
    marginBottom: TOKENS.spacing.xs,
  },
});

import React, { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { Text, Button, Menu } from 'react-native-paper';
import { CardFilter } from '../../constants/cardFilters';

interface Props {
  studyFilter: CardFilter;
  onFilterChange: (filter: CardFilter) => void;
  t: (key: string, replacements?: any) => string;
}

export function StudyScopeSection({ studyFilter, onFilterChange, t }: Props) {
  const [scopeMenuVisible, setScopeMenuVisible] = useState(false);

  // Normalize mapping for translations
  const filterKey = studyFilter === 'new+review' ? 'new_review' : studyFilter;

  return (
    <View style={styles.container}>
      <Text variant="titleMedium" style={styles.sectionTitle}>
        {t('settings.study_scope')}
      </Text>
      <Menu
        visible={scopeMenuVisible}
        onDismiss={() => setScopeMenuVisible(false)}
        anchor={
          <Button
            mode="outlined"
            onPress={() => setScopeMenuVisible(true)}
            style={styles.dropdownAnchor}
            accessibilityLabel="Study scope filter selection"
          >
            {t(`filter.${filterKey || 'new_review'}`)}
          </Button>
        }
      >
        <Menu.Item
          onPress={() => {
            onFilterChange('new+review');
            setScopeMenuVisible(false);
          }}
          title={t('filter.new_review')}
        />
        <Menu.Item
          onPress={() => {
            onFilterChange('new');
            setScopeMenuVisible(false);
          }}
          title={t('filter.new')}
        />
        <Menu.Item
          onPress={() => {
            onFilterChange('review');
            setScopeMenuVisible(false);
          }}
          title={t('filter.review')}
        />
        <Menu.Item
          onPress={() => {
            onFilterChange('all');
            setScopeMenuVisible(false);
          }}
          title={t('filter.all')}
        />
      </Menu>
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
  dropdownAnchor: {
    alignSelf: 'stretch',
    marginBottom: 12,
  },
});

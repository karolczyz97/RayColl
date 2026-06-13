import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Text } from 'react-native-paper';
import { AppSelect } from '@/components/AppSelect';
import { CARD_ORDER_OPTIONS, normalizeCardOrder, type CardOrder } from '@/constants/cardOrder';
import { TOKENS } from '@/theme/tokens';
import { useI18n } from '@/i18n';

interface CardOrderSectionProps {
  cardOrder: CardOrder | undefined;
  onCardOrderChange: (order: CardOrder) => void;
}

export function CardOrderSection({ cardOrder, onCardOrderChange }: CardOrderSectionProps) {
  const { t } = useI18n();
  const options = CARD_ORDER_OPTIONS.map((option) => ({
    label: t(option.labelKey),
    value: option.value,
  }));

  return (
    <View style={styles.container}>
      <Text variant="titleMedium" style={styles.sectionTitle}>
        {t('settings.card_order')}
      </Text>
      <AppSelect
        value={normalizeCardOrder(cardOrder)}
        options={options}
        onChange={(value) => onCardOrderChange(normalizeCardOrder(value))}
        accessibilityLabel="Card order selection"
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

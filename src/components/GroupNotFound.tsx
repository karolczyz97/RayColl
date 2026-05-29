import React from 'react';
import { View, StyleSheet } from 'react-native';
import { Text, Button, useTheme } from 'react-native-paper';
import { useI18n } from '../i18n';
import { TOKENS } from '../theme/tokens';

interface GroupNotFoundProps {
  onBack: () => void;
}

export function GroupNotFound({ onBack }: GroupNotFoundProps) {
  const { t } = useI18n();
  const theme = useTheme();
  return (
    <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <Text variant="headlineSmall" style={[styles.text, { color: theme.colors.onSurface }]}>
        {t('study.group_not_found')}
      </Text>
      <Button mode="contained" onPress={onBack}>
        {t('btn.back')}
      </Button>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: TOKENS.spacing.xl,
    alignItems: 'center',
    justifyContent: 'center',
    flex: 1,
  },
  text: {
    marginBottom: TOKENS.spacing.lg,
    fontWeight: 'bold',
    textAlign: 'center',
  },
});

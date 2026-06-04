import React from 'react';
import { View, StyleSheet, ImageSourcePropType } from 'react-native';
import { Avatar, Text, useTheme } from 'react-native-paper';
import { useI18n } from '@/i18n';
import { TOKENS } from '@/theme/tokens';

interface UserMenuHeaderProps {
  source: ImageSourcePropType;
  displayName?: string | null;
  email?: string | null;
}

export function UserMenuHeader({ source, displayName, email }: UserMenuHeaderProps) {
  const { t } = useI18n();
  const theme = useTheme();

  return (
    <View style={styles.container}>
      <Avatar.Image size={TOKENS.iconSize.xl} source={source} style={styles.avatar} />
      <Text variant="titleMedium" style={styles.name}>
        {displayName || t('auth.local')}
      </Text>
      <Text
        variant="bodySmall"
        style={[styles.email, { color: theme.colors.onSurfaceVariant }]}
      >
        {email}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    padding: TOKENS.spacing.lg,
    alignItems: 'center',
  },
  avatar: {
    marginBottom: TOKENS.spacing.sm,
  },
  name: {
    fontWeight: TOKENS.typography.weight.bold,
  },
  email: {
    marginBottom: TOKENS.spacing.sm,
  },
});

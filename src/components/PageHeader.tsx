import React from 'react';
import { View, StyleSheet } from 'react-native';
import { IconButton, Text } from 'react-native-paper';

interface PageHeaderProps {
  title: string;
  onBack: () => void;
  action?: React.ReactNode;
}

export function PageHeader({ title, onBack, action }: PageHeaderProps) {
  return (
    <View style={styles.container}>
      <IconButton icon="arrow-left" size={24} onPress={onBack} style={styles.backBtn} />
      <Text variant="titleLarge" style={styles.title} numberOfLines={1}>
        {title}
      </Text>
      {action}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 8,
    gap: 8,
  },
  backBtn: {
    margin: 0,
  },
  title: {
    fontWeight: 'bold',
    flex: 1,
  },
});

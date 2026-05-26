import React from 'react';
import { StyleSheet } from 'react-native';
import { IconButton, Text } from 'react-native-paper';
import Animated, { FadeIn } from 'react-native-reanimated';

interface PageHeaderProps {
  title: string;
  onBack: () => void;
  action?: React.ReactNode;
}

export function PageHeader({ title, onBack, action }: PageHeaderProps) {
  return (
    <Animated.View entering={FadeIn.duration(300)} style={styles.container}>
      <IconButton icon="arrow-left" size={24} onPress={onBack} style={styles.backBtn} />
      <Text variant="titleLarge" style={styles.title} numberOfLines={1}>
        {title}
      </Text>
      {action}
    </Animated.View>
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

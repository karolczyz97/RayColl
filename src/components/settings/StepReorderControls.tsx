import React from 'react';
import { View, StyleSheet } from 'react-native';
import { IconButton, useTheme } from 'react-native-paper';
import { TOKENS } from '../../theme/tokens';

interface Props {
  index: number;
  isFirst: boolean;
  isLast: boolean;
  onMoveUp: () => void;
  onMoveDown: () => void;
  onDelete: () => void;
}

export function StepReorderControls({
  index,
  isFirst,
  isLast,
  onMoveUp,
  onMoveDown,
  onDelete,
}: Props) {
  const theme = useTheme();
  const position = index + 1;

  return (
    <View style={styles.controls}>
      <IconButton
        icon="arrow-up"
        size={TOKENS.iconSize.xs}
        style={styles.button}
        onPress={onMoveUp}
        disabled={isFirst}
        accessibilityLabel={`Move step ${position} up`}
      />
      <IconButton
        icon="arrow-down"
        size={TOKENS.iconSize.xs}
        style={styles.button}
        onPress={onMoveDown}
        disabled={isLast}
        accessibilityLabel={`Move step ${position} down`}
      />
      <IconButton
        icon="delete"
        size={TOKENS.iconSize.xs}
        style={styles.button}
        iconColor={theme.colors.error}
        onPress={onDelete}
        accessibilityLabel={`Delete step ${position}`}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  controls: {
    flexDirection: 'row',
  },
  button: {
    margin: 0,
  },
});

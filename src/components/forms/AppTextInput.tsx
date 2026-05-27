import React from 'react';
import { StyleSheet } from 'react-native';
import { TextInput, type TextInputProps } from 'react-native-paper';
import { TOKENS } from '../../theme/tokens';

export function AppTextInput(props: TextInputProps) {
  return (
    <TextInput
      mode="outlined"
      {...props}
      style={[styles.input, props.style]}
      outlineStyle={[styles.outline, props.outlineStyle]}
    />
  );
}

const styles = StyleSheet.create({
  input: {
    minHeight: TOKENS.control.height,
  },
  outline: {
    borderRadius: TOKENS.control.borderRadius,
  },
});

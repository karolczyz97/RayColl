import React from 'react';
import { AppTextInput } from './AppTextInput';

interface AppNumberInputProps {
  label: string;
  value: number;
  onChange: (value: number) => void;
  min?: number;
  max?: number;
  accessibilityLabel?: string;
}

export function AppNumberInput({
  label,
  value,
  onChange,
  min,
  max,
  accessibilityLabel,
}: AppNumberInputProps) {
  return (
    <AppTextInput
      label={label}
      keyboardType="numeric"
      value={String(value)}
      accessibilityLabel={accessibilityLabel}
      onChangeText={(text) => {
        const parsed = Number(text);
        const safe = Number.isFinite(parsed) ? parsed : 0;
        const clamped = Math.min(max ?? safe, Math.max(min ?? safe, safe));
        onChange(clamped);
      }}
    />
  );
}

import React, { useEffect, useRef, useState } from 'react';
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
  const [text, setText] = useState<string>(String(value));
  const lastValueRef = useRef(value);

  // Resync the field only when the numeric value changes from outside (e.g. a
  // clamp or a parent update). Clearing the field does not push a change, so
  // the value stays put and the field is allowed to remain empty while typing.
  useEffect(() => {
    if (value !== lastValueRef.current) {
      lastValueRef.current = value;
      setText(String(value));
    }
  }, [value]);

  const handleChangeText = (next: string) => {
    setText(next);
    if (next.trim() === '') {
      // Keep the field empty instead of snapping to 0/min mid-edit.
      return;
    }
    const parsed = Number(next);
    if (!Number.isFinite(parsed)) return;
    const clamped = Math.min(max ?? parsed, Math.max(min ?? parsed, parsed));
    onChange(clamped);
  };

  return (
    <AppTextInput
      label={label}
      keyboardType="numeric"
      value={text}
      accessibilityLabel={accessibilityLabel}
      onChangeText={handleChangeText}
    />
  );
}

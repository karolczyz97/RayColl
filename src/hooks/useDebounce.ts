import { useEffect, useState } from 'react';

export function useDebounce<T>(value: T, delayMs = 350): T {
  const [debouncedValue, setDebouncedValue] = useState(value);

  useEffect(() => {
    const id = setTimeout(() => {
      setDebouncedValue(value);
    }, delayMs);

    return () => clearTimeout(id);
  }, [value, delayMs]);

  return debouncedValue;
}

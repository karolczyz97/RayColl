import { useCallback, useEffect, useRef, useState } from 'react';

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

export function useDebouncedCallback<TArgs extends unknown[]>(
  callback: (...args: TArgs) => void,
  delayMs = 350,
) {
  const callbackRef = useRef(callback);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastArgsRef = useRef<TArgs | null>(null);

  useEffect(() => {
    callbackRef.current = callback;
  }, [callback]);

  const cancel = useCallback(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    lastArgsRef.current = null;
  }, []);

  const flush = useCallback(() => {
    if (!lastArgsRef.current) return;

    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }

    const args = lastArgsRef.current;
    lastArgsRef.current = null;
    callbackRef.current(...args);
  }, []);

  const run = useCallback(
    (...args: TArgs) => {
      lastArgsRef.current = args;

      if (timerRef.current) {
        clearTimeout(timerRef.current);
      }

      timerRef.current = setTimeout(() => {
        timerRef.current = null;
        const latestArgs = lastArgsRef.current;
        lastArgsRef.current = null;

        if (latestArgs) {
          callbackRef.current(...latestArgs);
        }
      }, delayMs);
    },
    [delayMs],
  );

  useEffect(() => cancel, [cancel]);

  return { run, flush, cancel };
}

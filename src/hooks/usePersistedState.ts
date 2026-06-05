import { useState, useEffect, useCallback, useRef } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { getErrorMessage } from '@/utils/errors';

interface UsePersistedStateOptions<T> {
  key: string;
  parse: (raw: string | null) => T;
  serialize: (val: T) => string;
  fallback: T;
}

export function usePersistedState<T>({
  key,
  parse,
  serialize,
  fallback,
}: UsePersistedStateOptions<T>) {
  const [value, setValueState] = useState<T>(fallback);
  const [isLoading, setIsLoading] = useState(true);

  const parseRef = useRef(parse);
  const serializeRef = useRef(serialize);

  useEffect(() => {
    parseRef.current = parse;
    serializeRef.current = serialize;
  });

  useEffect(() => {
    let active = true;

    async function load() {
      try {
        const raw = await AsyncStorage.getItem(key);
        if (!active) return;
        setValueState(parseRef.current(raw));
      } catch (err) {
        console.warn(`Failed to load ${key}:`, getErrorMessage(err));
      } finally {
        if (active) setIsLoading(false);
      }
    }

    load();
    return () => { active = false; };
  }, [key]);

  const setValue = useCallback(async (val: T) => {
    setValueState(val);
    try {
      await AsyncStorage.setItem(key, serializeRef.current(val));
    } catch (err) {
      console.warn(`Failed to save ${key}:`, getErrorMessage(err));
    }
  }, [key]);

  return { value, setValue, isLoading };
}

import React, { createContext, useContext, useState, useEffect } from 'react';
import { useColorScheme } from 'react-native';
import { STORAGE_KEYS } from '@/constants/storageKeys';
import { loadUiPreferences, setUiPreference } from '@/storage/uiPreferences';
import { clamp } from '@/utils/math';

export type ThemePref = 'light' | 'dark' | 'system';

interface ThemeContextType {
  themePref: ThemePref;
  setThemePref: (pref: ThemePref) => Promise<void>;
  isDark: boolean;
  useSystemColors: boolean;
  setUseSystemColors: (val: boolean) => Promise<void>;
  isThemeLoading: boolean;
  ttsRate: number;
  setTtsRate: (val: number) => Promise<void>;
  /** Whether the rail is expanded (labels) vs collapsed (icons only). */
  railExpanded: boolean;
  setRailExpanded: (val: boolean) => Promise<void>;
  /** Background playback toggle (Android-only lock screen audio). */
  backgroundPlayback: boolean;
  setBackgroundPlayback: (val: boolean) => Promise<void>;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);
const MIN_TTS_RATE = 0.5;
const MAX_TTS_RATE = 2.0;

export function isThemePref(value: string): value is ThemePref {
  return value === 'light' || value === 'dark' || value === 'system';
}

function clampTtsRate(value: number): number {
  if (!Number.isFinite(value)) {
    return 1.0;
  }

  return clamp(value, MIN_TTS_RATE, MAX_TTS_RATE);
}

function createPreferenceSetter<T>({
  setState,
  key,
  serialize,
  mapValue,
}: {
  setState: (val: T) => void;
  key: string;
  serialize: (val: T) => string;
  mapValue?: (val: T) => T;
}): (val: T) => Promise<void> {
  return async (val: T) => {
    const next = mapValue ? mapValue(val) : val;
    setState(next);
    try {
      await setUiPreference(key, serialize(next));
    } catch (err) {
      console.warn(`Failed to save ${key}:`, err);
    }
  };
}

export function useAppTheme() {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useAppTheme must be used within an AppThemeProvider');
  }
  return context;
}

export function AppThemeProvider({ children }: { children: React.ReactNode }) {
  const systemScheme = useColorScheme();
  const [themePref, setThemePrefState] = useState<ThemePref>('system');
  const [useSystemColors, setUseSystemColorsState] = useState<boolean>(true); // enabled by default
  const [isThemeLoading, setIsThemeLoading] = useState<boolean>(true);
  const [ttsRate, setTtsRateState] = useState<number>(1.0);
  const [railExpanded, setRailExpandedState] = useState<boolean>(false); // collapsed by default
  const [backgroundPlayback, setBackgroundPlaybackState] = useState<boolean>(false); // off by default

  useEffect(() => {
    let active = true;

    async function loadSettings() {
      try {
        const settings = await loadUiPreferences();
        const savedTheme = settings[STORAGE_KEYS.THEME_PREF];
        if (active && savedTheme && isThemePref(savedTheme)) {
          setThemePrefState(savedTheme);
        }

        const savedSysColors = settings[STORAGE_KEYS.USE_SYSTEM_COLORS];
        if (active && savedSysColors !== null) {
          setUseSystemColorsState(savedSysColors === 'true');
        }

        const savedTtsRate = settings[STORAGE_KEYS.TTS_RATE];
        if (active && savedTtsRate !== null) {
          setTtsRateState(clampTtsRate(parseFloat(savedTtsRate)));
        }

        const savedRailExpanded = settings[STORAGE_KEYS.NAV_RAIL_EXPANDED];
        if (active && savedRailExpanded !== null) {
          setRailExpandedState(savedRailExpanded === 'true');
        }

        const savedBackgroundPlayback = settings[STORAGE_KEYS.BACKGROUND_PLAYBACK];
        if (active && savedBackgroundPlayback !== null) {
          setBackgroundPlaybackState(savedBackgroundPlayback === 'true');
        }
      } catch (err) {
        console.warn('Failed to load theme settings:', err);
      } finally {
        if (active) {
          setIsThemeLoading(false);
        }
      }
    }
    loadSettings();

    return () => {
      active = false;
    };
  }, []);

  const setThemePref = createPreferenceSetter<ThemePref>({
    setState: setThemePrefState,
    key: STORAGE_KEYS.THEME_PREF,
    serialize: (v) => v,
  });

  const setUseSystemColors = createPreferenceSetter<boolean>({
    setState: setUseSystemColorsState,
    key: STORAGE_KEYS.USE_SYSTEM_COLORS,
    serialize: (v) => (v ? 'true' : 'false'),
  });

  const setTtsRate = createPreferenceSetter<number>({
    setState: setTtsRateState,
    key: STORAGE_KEYS.TTS_RATE,
    serialize: String,
    mapValue: clampTtsRate,
  });

  const setRailExpanded = createPreferenceSetter<boolean>({
    setState: setRailExpandedState,
    key: STORAGE_KEYS.NAV_RAIL_EXPANDED,
    serialize: (v) => (v ? 'true' : 'false'),
  });

  const setBackgroundPlayback = createPreferenceSetter<boolean>({
    setState: setBackgroundPlaybackState,
    key: STORAGE_KEYS.BACKGROUND_PLAYBACK,
    serialize: (v) => (v ? 'true' : 'false'),
  });

  const isDark = themePref === 'system' ? systemScheme === 'dark' : themePref === 'dark';

  return (
    <ThemeContext.Provider
      value={{
        themePref,
        setThemePref,
        isDark,
        useSystemColors,
        setUseSystemColors,
        isThemeLoading,
        ttsRate,
        setTtsRate,
        railExpanded,
        setRailExpanded,
        backgroundPlayback,
        setBackgroundPlayback,
      }}
    >
      {children}
    </ThemeContext.Provider>
  );
}

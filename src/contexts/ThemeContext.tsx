import React, { createContext, useContext, useState, useEffect } from 'react';
import { useColorScheme } from 'react-native';
import { STORAGE_KEYS } from '../constants/storageKeys';
import { loadUiPreferences, setUiPreference } from '../storage/uiPreferences';

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
  /** Whether the persistent navigation rail is shown on >=600px widths (user preference). */
  railVisible: boolean;
  setRailVisible: (val: boolean) => Promise<void>;
  /** Whether the rail is expanded (labels) vs collapsed (icons only). */
  railExpanded: boolean;
  setRailExpanded: (val: boolean) => Promise<void>;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);
const MIN_TTS_RATE = 0.5;
const MAX_TTS_RATE = 2.0;

function isThemePref(value: string): value is ThemePref {
  return value === 'light' || value === 'dark' || value === 'system';
}

function clampTtsRate(value: number): number {
  if (!Number.isFinite(value)) {
    return 1.0;
  }

  return Math.max(MIN_TTS_RATE, Math.min(MAX_TTS_RATE, value));
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
  const [railVisible, setRailVisibleState] = useState<boolean>(true); // shown by default on >=600px
  const [railExpanded, setRailExpandedState] = useState<boolean>(false); // collapsed by default

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

        const savedRailVisible = settings[STORAGE_KEYS.NAV_RAIL_VISIBLE];
        if (active && savedRailVisible !== null) {
          setRailVisibleState(savedRailVisible === 'true');
        }

        const savedRailExpanded = settings[STORAGE_KEYS.NAV_RAIL_EXPANDED];
        if (active && savedRailExpanded !== null) {
          setRailExpandedState(savedRailExpanded === 'true');
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

  const setThemePref = async (pref: ThemePref) => {
    setThemePrefState(pref);
    try {
      await setUiPreference(STORAGE_KEYS.THEME_PREF, pref);
    } catch (err) {
      console.warn('Failed to save theme preference:', err);
    }
  };

  const setUseSystemColors = async (val: boolean) => {
    setUseSystemColorsState(val);
    try {
      await setUiPreference(STORAGE_KEYS.USE_SYSTEM_COLORS, val ? 'true' : 'false');
    } catch (err) {
      console.warn('Failed to save system colors preference:', err);
    }
  };

  const setTtsRate = async (val: number) => {
    const nextValue = clampTtsRate(val);
    setTtsRateState(nextValue);
    try {
      await setUiPreference(STORAGE_KEYS.TTS_RATE, String(nextValue));
    } catch (err) {
      console.warn('Failed to save TTS rate:', err);
    }
  };

  const setRailVisible = async (val: boolean) => {
    setRailVisibleState(val);
    try {
      await setUiPreference(STORAGE_KEYS.NAV_RAIL_VISIBLE, val ? 'true' : 'false');
    } catch (err) {
      console.warn('Failed to save rail visibility preference:', err);
    }
  };

  const setRailExpanded = async (val: boolean) => {
    setRailExpandedState(val);
    try {
      await setUiPreference(STORAGE_KEYS.NAV_RAIL_EXPANDED, val ? 'true' : 'false');
    } catch (err) {
      console.warn('Failed to save rail expanded preference:', err);
    }
  };

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
        railVisible,
        setRailVisible,
        railExpanded,
        setRailExpanded,
      }}
    >
      {children}
    </ThemeContext.Provider>
  );
}

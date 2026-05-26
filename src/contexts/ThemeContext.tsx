import React, { createContext, useContext, useState, useEffect } from 'react';
import { useColorScheme } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { STORAGE_KEYS } from '../constants/storageKeys';

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
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

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

  useEffect(() => {
    async function loadSettings() {
      try {
        const savedTheme = await AsyncStorage.getItem(STORAGE_KEYS.THEME_PREF);
        if (
          savedTheme &&
          (savedTheme === 'light' || savedTheme === 'dark' || savedTheme === 'system')
        ) {
          setThemePrefState(savedTheme as ThemePref);
        }

        const savedSysColors = await AsyncStorage.getItem(STORAGE_KEYS.USE_SYSTEM_COLORS);
        if (savedSysColors !== null) {
          setUseSystemColorsState(savedSysColors === 'true');
        }

        const savedTtsRate = await AsyncStorage.getItem(STORAGE_KEYS.TTS_RATE);
        if (savedTtsRate !== null) {
          setTtsRateState(parseFloat(savedTtsRate));
        }
      } catch (err) {
        console.warn('Failed to load theme settings:', err);
      } finally {
        setIsThemeLoading(false);
      }
    }
    loadSettings();
  }, []);

  const setThemePref = async (pref: ThemePref) => {
    setThemePrefState(pref);
    try {
      await AsyncStorage.setItem(STORAGE_KEYS.THEME_PREF, pref);
    } catch (err) {
      console.warn('Failed to save theme preference:', err);
    }
  };

  const setUseSystemColors = async (val: boolean) => {
    setUseSystemColorsState(val);
    try {
      await AsyncStorage.setItem(STORAGE_KEYS.USE_SYSTEM_COLORS, val ? 'true' : 'false');
    } catch (err) {
      console.warn('Failed to save system colors preference:', err);
    }
  };

  const setTtsRate = async (val: number) => {
    setTtsRateState(val);
    try {
      await AsyncStorage.setItem(STORAGE_KEYS.TTS_RATE, String(val));
    } catch (err) {
      console.warn('Failed to save TTS rate:', err);
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
      }}
    >
      {children}
    </ThemeContext.Provider>
  );
}

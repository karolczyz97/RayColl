import React, { createContext, useContext, useState, useEffect } from 'react';
import { useColorScheme } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

export type ThemePref = 'light' | 'dark' | 'system';

interface ThemeContextType {
  themePref: ThemePref;
  setThemePref: (pref: ThemePref) => void;
  isDark: boolean;
  useSystemColors: boolean;
  setUseSystemColors: (val: boolean) => void;
  isThemeLoading: boolean;
}

const ThemeContext = createContext<ThemeContextType>(null!);

export const useAppTheme = () => useContext(ThemeContext);

export function AppThemeProvider({ children }: { children: React.ReactNode }) {
  const systemScheme = useColorScheme();
  const [themePref, setThemePrefState] = useState<ThemePref>('system');
  const [useSystemColors, setUseSystemColorsState] = useState<boolean>(true); // enabled by default
  const [isThemeLoading, setIsThemeLoading] = useState<boolean>(true);

  useEffect(() => {
    async function loadSettings() {
      try {
        const savedTheme = await AsyncStorage.getItem('td-theme');
        if (savedTheme && (savedTheme === 'light' || savedTheme === 'dark' || savedTheme === 'system')) {
          setThemePrefState(savedTheme as ThemePref);
        }
        
        const savedSysColors = await AsyncStorage.getItem('td-use-system-colors');
        if (savedSysColors !== null) {
          setUseSystemColorsState(savedSysColors === 'true');
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
      await AsyncStorage.setItem('td-theme', pref);
    } catch (err) {
      console.warn('Failed to save theme preference:', err);
    }
  };

  const setUseSystemColors = async (val: boolean) => {
    setUseSystemColorsState(val);
    try {
      await AsyncStorage.setItem('td-use-system-colors', val ? 'true' : 'false');
    } catch (err) {
      console.warn('Failed to save system colors preference:', err);
    }
  };

  const isDark = themePref === 'system' ? systemScheme === 'dark' : themePref === 'dark';

  return (
    <ThemeContext.Provider value={{ themePref, setThemePref, isDark, useSystemColors, setUseSystemColors, isThemeLoading }}>
      {children}
    </ThemeContext.Provider>
  );
}

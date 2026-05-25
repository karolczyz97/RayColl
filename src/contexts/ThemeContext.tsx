import React, { createContext, useContext, useState, useEffect } from 'react';
import { useColorScheme } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';

export type ThemePref = 'light' | 'dark' | 'system';

interface ThemeContextType {
  themePref: ThemePref;
  setThemePref: (pref: ThemePref) => void;
  isDark: boolean;
}

const ThemeContext = createContext<ThemeContextType>(null!);

export const useAppTheme = () => useContext(ThemeContext);

export function AppThemeProvider({ children }: { children: React.ReactNode }) {
  const systemScheme = useColorScheme();
  const [themePref, setThemePrefState] = useState<ThemePref>('system');

  useEffect(() => {
    async function loadTheme() {
      try {
        const saved = await AsyncStorage.getItem('td-theme');
        if (saved && (saved === 'light' || saved === 'dark' || saved === 'system')) {
          setThemePrefState(saved as ThemePref);
        }
      } catch (err) {
        console.warn('Failed to load theme preference:', err);
      }
    }
    loadTheme();
  }, []);

  const setThemePref = async (pref: ThemePref) => {
    setThemePrefState(pref);
    try {
      await AsyncStorage.setItem('td-theme', pref);
    } catch (err) {
      console.warn('Failed to save theme preference:', err);
    }
  };

  const isDark = themePref === 'system' ? systemScheme === 'dark' : themePref === 'dark';

  return (
    <ThemeContext.Provider value={{ themePref, setThemePref, isDark }}>
      {children}
    </ThemeContext.Provider>
  );
}

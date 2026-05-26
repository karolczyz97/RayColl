import React, {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
  type ReactNode,
} from 'react';
import { Platform } from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { pl } from './locales/pl';
import { en } from './locales/en';
import { de } from './locales/de';
import { es } from './locales/es';
import { it } from './locales/it';

export type LanguageCode = 'pl' | 'en' | 'de' | 'es' | 'it';
export type TranslationReplacements = Record<string, string | number>;
export type TranslationFn = (key: string, replacements?: TranslationReplacements) => string;

const translations: Record<LanguageCode, Record<string, string>> = { pl, en, de, es, it };

interface I18nContextType {
  language: LanguageCode;
  setLanguage: (lang: LanguageCode) => void;
  t: TranslationFn;
  isI18nLoading: boolean;
}

const I18nContext = createContext<I18nContextType>(null!);

export const useI18n = () => useContext(I18nContext);

export function I18nProvider({ children }: { children: ReactNode }) {
  const [language, setLanguageState] = useState<LanguageCode>('en');
  const [isI18nLoading, setIsI18nLoading] = useState(true);

  // Load language settings on mount
  useEffect(() => {
    async function loadSavedLanguage() {
      try {
        const saved = await AsyncStorage.getItem('td-lang');
        if (
          saved &&
          (saved === 'pl' || saved === 'en' || saved === 'de' || saved === 'es' || saved === 'it')
        ) {
          setLanguageState(saved as LanguageCode);
        } else if (Platform.OS === 'web' && typeof navigator !== 'undefined') {
          // Auto-detect browser language on web
          const browserLang = navigator.language.slice(0, 2);
          if (
            browserLang === 'pl' ||
            browserLang === 'de' ||
            browserLang === 'es' ||
            browserLang === 'it'
          ) {
            setLanguageState(browserLang as LanguageCode);
          }
        }
      } catch (err) {
        console.warn('Failed to load saved language:', err);
      } finally {
        setIsI18nLoading(false);
      }
    }
    loadSavedLanguage();
  }, []);

  const setLanguage = useCallback(async (lang: LanguageCode) => {
    setLanguageState(lang);
    try {
      await AsyncStorage.setItem('td-lang', lang);
    } catch (err) {
      console.warn('Failed to save language:', err);
    }
  }, []);

  const t = useCallback(
    (key: string, replacements?: Record<string, string | number>): string => {
      const langDict = translations[language] || translations['en'];
      let val = langDict[key] || translations['en'][key] || key;
      if (replacements) {
        Object.entries(replacements).forEach(([k, v]) => {
          val = val.replace(`{${k}}`, String(v));
        });
      }
      return val;
    },
    [language],
  );

  // Sync html lang attribute on web
  useEffect(() => {
    if (Platform.OS === 'web' && typeof document !== 'undefined') {
      document.documentElement.lang = language;
    }
  }, [language]);

  return React.createElement(
    I18nContext.Provider,
    { value: { language, setLanguage, t, isI18nLoading } },
    children,
  );
}

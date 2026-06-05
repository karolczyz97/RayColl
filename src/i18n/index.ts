import React, {
  createContext,
  useContext,
  useEffect,
  useCallback,
  type ReactNode,
} from 'react';
import { Platform } from 'react-native';
import { pl } from './locales/pl';
import { en } from './locales/en';
import { de } from './locales/de';
import { es } from './locales/es';
import { it } from './locales/it';
import { STORAGE_KEYS } from '@/constants/storageKeys';
import { usePersistedState } from '@/hooks/usePersistedState';

export type LanguageCode = 'pl' | 'en' | 'de' | 'es' | 'it';
export type TranslationReplacements = Record<string, string | number>;
export type TranslationFn = (key: string, replacements?: TranslationReplacements) => string;

const translations: Record<LanguageCode, Record<string, string>> = { pl, en, de, es, it };
const LANGUAGE_CODES: LanguageCode[] = ['pl', 'en', 'de', 'es', 'it'];

function isLanguageCode(value: string): value is LanguageCode {
  return LANGUAGE_CODES.some((code) => code === value);
}

function detectBrowserLanguage(): LanguageCode {
  if (Platform.OS === 'web' && typeof navigator !== 'undefined') {
    const browserLang = navigator.language.slice(0, 2);
    if (isLanguageCode(browserLang)) {
      return browserLang;
    }
  }
  return 'en';
}

interface I18nContextType {
  language: LanguageCode;
  setLanguage: (lang: LanguageCode) => void;
  t: TranslationFn;
  isI18nLoading: boolean;
}

const I18nContext = createContext<I18nContextType>(null!);

export const useI18n = () => useContext(I18nContext);

export function I18nProvider({ children }: { children: ReactNode }) {
  const { value: language, setValue: setLanguage, isLoading: isI18nLoading } =
    usePersistedState<LanguageCode>({
      key: STORAGE_KEYS.LANGUAGE,
      parse: (raw) => (raw && isLanguageCode(raw) ? raw : detectBrowserLanguage()),
      serialize: (val) => val,
      fallback: 'en',
    });

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

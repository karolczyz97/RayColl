import React, { createContext, useContext, useState, useEffect, useCallback, type ReactNode } from 'react';
import { pl } from './locales/pl';
import { en } from './locales/en';
import { de } from './locales/de';
import { es } from './locales/es';
import { it } from './locales/it';

export type LanguageCode = 'pl' | 'en' | 'de' | 'es' | 'it';

const translations: Record<LanguageCode, Record<string, string>> = { pl, en, de, es, it };

interface I18nContextType {
  language: LanguageCode;
  setLanguage: (lang: LanguageCode) => void;
  t: (key: string, replacements?: Record<string, string | number>) => string;
}

const I18nContext = createContext<I18nContextType>(null!);

export const useI18n = () => useContext(I18nContext);

export function I18nProvider({ children }: { children: ReactNode }) {
  const [language, setLanguageState] = useState<LanguageCode>(() => {
    if (typeof window !== 'undefined') {
      const saved = localStorage.getItem('td-lang');
      if (saved && (saved === 'pl' || saved === 'en' || saved === 'de' || saved === 'es' || saved === 'it')) {
        return saved as LanguageCode;
      }
      // Auto-detect browser language
      const browserLang = navigator.language.slice(0, 2);
      if (browserLang === 'pl' || browserLang === 'de' || browserLang === 'es' || browserLang === 'it') {
        return browserLang as LanguageCode;
      }
    }
    return 'en';
  });

  const setLanguage = useCallback((lang: LanguageCode) => {
    setLanguageState(lang);
    localStorage.setItem('td-lang', lang);
  }, []);

  const t = useCallback((key: string, replacements?: Record<string, string | number>): string => {
    const langDict = translations[language] || translations['en'];
    let val = langDict[key] || translations['en'][key] || key;
    if (replacements) {
      Object.entries(replacements).forEach(([k, v]) => {
        val = val.replace(`{${k}}`, String(v));
      });
    }
    return val;
  }, [language]);

  // Sync html lang attribute
  useEffect(() => {
    if (typeof document !== 'undefined') {
      document.documentElement.lang = language;
    }
  }, [language]);

  return React.createElement(I18nContext.Provider, { value: { language, setLanguage, t } }, children);
}

import { describe, expect, it } from '@jest/globals';
import { readdirSync, readFileSync } from 'node:fs';
import * as path from 'node:path';

import { en } from '../locales/en';
import { pl } from '../locales/pl';
import { de } from '../locales/de';
import { es } from '../locales/es';
import { it as itLocale } from '../locales/it';

const LOCALES = { pl, de, es, it: itLocale } as const;

const PLACEHOLDER_RE = /\{[a-zA-Z_]+\}/g;

function extractPlaceholders(str: string): string[] {
  return [...str.matchAll(PLACEHOLDER_RE)].map((m) => m[0]).sort();
}

function getSourceFiles(dir: string): string[] {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const fullPath = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      return getSourceFiles(fullPath);
    }
    return /\.(ts|tsx)$/.test(entry.name) ? [fullPath] : [];
  });
}

describe('i18n locale consistency', () => {
  const enKeys = Object.keys(en).sort();
  const enKeySet = new Set(enKeys);

  it('en locale is not empty', () => {
    expect(enKeys.length).toBeGreaterThan(0);
  });

  it('all literal translation keys used in src exist in en', () => {
    const srcDir = path.join(process.cwd(), 'src');
    const missingKeys = new Set<string>();
    const usages: string[] = [];

    for (const file of getSourceFiles(srcDir)) {
      const source = readFileSync(file, 'utf8');
      for (const match of source.matchAll(/\bt\(\s*'([^']+)'/g)) {
        const key = match[1];
        if (!enKeySet.has(key)) {
          missingKeys.add(key);
          usages.push(`${path.relative(process.cwd(), file)}: ${key}`);
        }
      }
    }

    expect(usages).toEqual([]);
    expect([...missingKeys]).toEqual([]);
  });

  for (const [name, locale] of Object.entries(LOCALES)) {
    it(`${name} has the same keys as en`, () => {
      const localeKeys = Object.keys(locale).sort();
      const missing = enKeys.filter((k) => !(k in locale));
      const extra = localeKeys.filter((k) => !enKeys.includes(k));

      expect(missing).toEqual([]);
      expect(extra).toEqual([]);
    });

    it(`${name} has no empty values`, () => {
      const empty = Object.entries(locale as Record<string, string>).filter(
        ([, v]) => v.trim() === '',
      );
      expect(empty).toEqual([]);
    });

    it(`${name} has consistent placeholders with en`, () => {
      const mismatches: string[] = [];
      for (const key of enKeys) {
        const enVal = en[key];
        const locVal = (locale as Record<string, string>)[key];
        if (!enVal || !locVal) continue;
        const enPlaceholders = extractPlaceholders(enVal);
        const locPlaceholders = extractPlaceholders(locVal);
        if (enPlaceholders.join(',') !== locPlaceholders.join(',')) {
          mismatches.push(
            `${key}: en=[${enPlaceholders.join(',')}] ${name}=[${locPlaceholders.join(',')}]`,
          );
        }
      }
      expect(mismatches).toEqual([]);
    });
  }
});

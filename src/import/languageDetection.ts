const LANG_TOKENS_EN_EXACT = ['word', 'phrase', 'english', 'translation', 'example', 'front', 'back', 'meaning', 'definition', 'en'];
const LANG_TOKENS_EN_PREFIX = ['angiel'];
const LANG_TOKENS_PL_EXACT = ['słowo', 'polish', 'pl', 'tłumaczenie', 'przykład'];
const LANG_TOKENS_PL_PREFIX = ['polsk', 'fraz'];
const LANG_TOKENS_ES_EXACT = ['palabra', 'es', 'spanish', 'espanol', 'traduccion', 'ejemplo'];
const LANG_TOKENS_ES_PREFIX = ['hiszpan'];
const LANG_TOKENS_DE_EXACT = ['wort', 'deutsch', 'de', 'ubersetzung', 'beispiel'];
const LANG_TOKENS_DE_PREFIX = ['niemiec'];
const LANG_TOKENS_FR_EXACT = ['french', 'francais', 'fr'];
const LANG_TOKENS_FR_PREFIX = ['franc'];
const LANG_TOKENS_IT_EXACT = ['italiano', 'italian', 'it'];
const LANG_TOKENS_IT_PREFIX = ['włos'];

function normalizeHeaderToken(text: string): string {
  return text
    .toLowerCase()
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .trim();
}

export function detectLangFromHeader(header: string): string {
  const h = normalizeHeaderToken(header);
  const words = h.split(/\s+/);

  if (words.some((w) => LANG_TOKENS_EN_EXACT.includes(w) || LANG_TOKENS_EN_PREFIX.some((p) => w.startsWith(p)))) return 'en-US';
  if (words.some((w) => LANG_TOKENS_PL_EXACT.includes(w) || LANG_TOKENS_PL_PREFIX.some((p) => w.startsWith(p)))) return 'pl-PL';
  if (words.some((w) => LANG_TOKENS_ES_EXACT.includes(w) || LANG_TOKENS_ES_PREFIX.some((p) => w.startsWith(p)))) return 'es-ES';
  if (words.some((w) => LANG_TOKENS_DE_EXACT.includes(w) || LANG_TOKENS_DE_PREFIX.some((p) => w.startsWith(p)))) return 'de-DE';
  if (words.some((w) => LANG_TOKENS_FR_EXACT.includes(w) || LANG_TOKENS_FR_PREFIX.some((p) => w.startsWith(p)))) return 'fr-FR';
  if (words.some((w) => LANG_TOKENS_IT_EXACT.includes(w) || LANG_TOKENS_IT_PREFIX.some((p) => w.startsWith(p)))) return 'it-IT';

  return 'en-US';
}

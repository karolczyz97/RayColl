import * as Crypto from 'expo-crypto';

let didWarnAboutInsecureFallback = false;

export function uid(): string {
  if (typeof Crypto.randomUUID === 'function') {
    return Crypto.randomUUID();
  }
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }

  if (!didWarnAboutInsecureFallback) {
    didWarnAboutInsecureFallback = true;
    console.warn('Falling back to Math.random-based UUID generation.');
  }

  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

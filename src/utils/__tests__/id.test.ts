import { describe, it, expect, afterEach, jest } from '@jest/globals';

const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const originalCryptoDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'crypto');

function setGlobalCrypto(value: Crypto | undefined) {
  Object.defineProperty(globalThis, 'crypto', {
    configurable: true,
    value,
  });
}

function restoreGlobalCrypto() {
  if (originalCryptoDescriptor) {
    Object.defineProperty(globalThis, 'crypto', originalCryptoDescriptor);
  } else {
    delete (globalThis as { crypto?: Crypto }).crypto;
  }
}

function loadUid(expoRandomUUID?: () => string) {
  jest.resetModules();
  jest.doMock('expo-crypto', () => ({
    randomUUID: expoRandomUUID,
  }));
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  return require('@/utils/id').uid as typeof import('@/utils/id').uid;
}

describe('uid', () => {
  afterEach(() => {
    jest.dontMock('expo-crypto');
    jest.restoreAllMocks();
    restoreGlobalCrypto();
  });

  it('prefers Expo Crypto randomUUID', () => {
    const uid = loadUid(() => 'expo-uuid');

    expect(uid()).toBe('expo-uuid');
  });

  it('falls back to global crypto.randomUUID when Expo Crypto is unavailable', () => {
    setGlobalCrypto({ randomUUID: () => 'global-uuid' } as unknown as Crypto);
    const uid = loadUid(undefined);

    expect(uid()).toBe('global-uuid');
  });

  it('uses the Math.random fallback only as a last resort and warns once', () => {
    setGlobalCrypto(undefined);
    const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
    const uid = loadUid(undefined);

    expect(uid()).toMatch(uuidRegex);
    expect(uid()).toMatch(uuidRegex);
    expect(warnSpy).toHaveBeenCalledTimes(1);
  });
});

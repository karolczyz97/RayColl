import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';

interface AuthPersistenceStorage {
  getItem(key: string): Promise<string | null>;
  setItem(key: string, value: string): Promise<void>;
  removeItem(key: string): Promise<void>;
}

const KEY_PREFIX = 'firebase_auth.';
const SECURE_STORE_OPTIONS: SecureStore.SecureStoreOptions = {
  keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK,
};

function toSecureStoreKey(key: string): string {
  const encoded = Array.from(key)
    .map((char) => char.charCodeAt(0).toString(36))
    .join('.');
  return `${KEY_PREFIX}${encoded}`;
}

async function canUseSecureStore(): Promise<boolean> {
  try {
    return await SecureStore.isAvailableAsync();
  } catch {
    return false;
  }
}

async function getSecureItem(key: string): Promise<string | null> {
  if (!(await canUseSecureStore())) return null;
  try {
    return await SecureStore.getItemAsync(toSecureStoreKey(key), SECURE_STORE_OPTIONS);
  } catch {
    return null;
  }
}

async function setSecureItem(key: string, value: string): Promise<boolean> {
  if (!(await canUseSecureStore())) return false;
  try {
    await SecureStore.setItemAsync(toSecureStoreKey(key), value, SECURE_STORE_OPTIONS);
    return true;
  } catch {
    return false;
  }
}

async function deleteSecureItem(key: string): Promise<void> {
  if (!(await canUseSecureStore())) return;
  try {
    await SecureStore.deleteItemAsync(toSecureStoreKey(key), SECURE_STORE_OPTIONS);
  } catch {
    // Removing auth state is best-effort across both persistence backends.
  }
}

async function deleteLegacyItem(key: string): Promise<void> {
  try {
    await AsyncStorage.removeItem(key);
  } catch {
    // A stale legacy auth copy should not break the secure persistence path.
  }
}

export const secureAuthPersistence: AuthPersistenceStorage = {
  async getItem(key) {
    const secureValue = await getSecureItem(key);
    if (secureValue !== null) return secureValue;

    const legacyValue = await AsyncStorage.getItem(key);
    if (legacyValue !== null && (await setSecureItem(key, legacyValue))) {
      await deleteLegacyItem(key);
    }
    return legacyValue;
  },

  async setItem(key, value) {
    if (await setSecureItem(key, value)) {
      await deleteLegacyItem(key);
      return;
    }
    await AsyncStorage.setItem(key, value);
  },

  async removeItem(key) {
    await Promise.allSettled([
      deleteSecureItem(key),
      deleteLegacyItem(key),
    ]);
  },
};

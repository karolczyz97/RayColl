import AsyncStorage from '@react-native-async-storage/async-storage';
import * as SecureStore from 'expo-secure-store';
import { secureAuthPersistence } from '../secureAuthPersistence';

const mockedSecureStore = SecureStore as jest.Mocked<typeof SecureStore>;

describe('secureAuthPersistence', () => {
  beforeEach(async () => {
    await AsyncStorage.clear();
    jest.clearAllMocks();
    mockedSecureStore.isAvailableAsync.mockResolvedValue(true);
  });

  it('stores auth state in SecureStore and clears the AsyncStorage copy', async () => {
    const key = 'firebase:authUser:test-app:user';

    await AsyncStorage.setItem(key, 'legacy');
    await secureAuthPersistence.setItem(key, 'secure');

    expect(mockedSecureStore.setItemAsync).toHaveBeenCalledWith(
      expect.stringMatching(/^firebase_auth\./),
      'secure',
      expect.objectContaining({ keychainAccessible: SecureStore.AFTER_FIRST_UNLOCK }),
    );
    await expect(AsyncStorage.getItem(key)).resolves.toBeNull();
    await expect(secureAuthPersistence.getItem(key)).resolves.toBe('secure');
  });

  it('migrates legacy AsyncStorage auth state on read', async () => {
    const key = 'firebase:authUser:test-app:legacy-user';

    await AsyncStorage.setItem(key, 'legacy');

    await expect(secureAuthPersistence.getItem(key)).resolves.toBe('legacy');
    expect(mockedSecureStore.setItemAsync).toHaveBeenCalledWith(
      expect.stringMatching(/^firebase_auth\./),
      'legacy',
      expect.any(Object),
    );
    await expect(AsyncStorage.getItem(key)).resolves.toBeNull();
  });

  it('falls back to AsyncStorage when SecureStore is unavailable', async () => {
    const key = 'firebase:authUser:test-app:fallback-user';
    mockedSecureStore.isAvailableAsync.mockResolvedValue(false);

    await secureAuthPersistence.setItem(key, 'fallback');

    expect(mockedSecureStore.setItemAsync).not.toHaveBeenCalled();
    await expect(AsyncStorage.getItem(key)).resolves.toBe('fallback');
    await expect(secureAuthPersistence.getItem(key)).resolves.toBe('fallback');
  });

  it('removes auth state from both persistence backends', async () => {
    const key = 'firebase:authUser:test-app:remove-user';

    await secureAuthPersistence.setItem(key, 'secure');
    await AsyncStorage.setItem(key, 'legacy');
    jest.clearAllMocks();

    await secureAuthPersistence.removeItem(key);

    expect(mockedSecureStore.deleteItemAsync).toHaveBeenCalledWith(
      expect.stringMatching(/^firebase_auth\./),
      expect.any(Object),
    );
    await expect(AsyncStorage.getItem(key)).resolves.toBeNull();
    await expect(secureAuthPersistence.getItem(key)).resolves.toBeNull();
  });
});

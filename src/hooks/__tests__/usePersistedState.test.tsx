import { renderHook, act } from '@testing-library/react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { usePersistedState } from '../usePersistedState';

describe('usePersistedState', () => {
  beforeEach(async () => {
    jest.restoreAllMocks();
    await AsyncStorage.clear();
  });

  it('starts with the fallback value and loading true', () => {
    const { result } = renderHook(() =>
      usePersistedState({
        key: 'test_key',
        parse: (raw) => (raw ? JSON.parse(raw) : 0),
        serialize: (v) => JSON.stringify(v),
        fallback: 0,
      }),
    );

    expect(result.current.value).toBe(0);
    expect(result.current.isLoading).toBe(true);
  });

  it('loads the persisted value from AsyncStorage', async () => {
    await AsyncStorage.setItem('test_key', '42');

    const { result } = renderHook(() =>
      usePersistedState({
        key: 'test_key',
        parse: (raw) => (raw ? JSON.parse(raw) : 0),
        serialize: (v) => JSON.stringify(v),
        fallback: 0,
      }),
    );

    await act(async () => {
      await new Promise((r) => setTimeout(r, 10));
    });

    expect(result.current.value).toBe(42);
    expect(result.current.isLoading).toBe(false);
  });

  it('uses fallback when stored value is missing', async () => {
    const { result } = renderHook(() =>
      usePersistedState({
        key: 'missing_key',
        parse: (raw) => (raw ? JSON.parse(raw) : 99),
        serialize: (v) => JSON.stringify(v),
        fallback: 99,
      }),
    );

    await act(async () => {
      await new Promise((r) => setTimeout(r, 10));
    });

    expect(result.current.value).toBe(99);
  });

  it('setValue persists to AsyncStorage and updates state', async () => {
    const { result } = renderHook(() =>
      usePersistedState({
        key: 'test_key',
        parse: (raw) => (raw ?? 'default'),
        serialize: (v) => v,
        fallback: 'default',
      }),
    );

    await act(async () => {
      await new Promise((r) => setTimeout(r, 10));
    });

    await act(async () => {
      await result.current.setValue('updated');
    });

    expect(result.current.value).toBe('updated');
    const stored = await AsyncStorage.getItem('test_key');
    expect(stored).toBe('updated');
  });

  it('survives AsyncStorage read errors gracefully', async () => {
    const errorSpy = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
    const getItemSpy = jest.spyOn(AsyncStorage, 'getItem').mockRejectedValueOnce(new Error('storage down'));

    const { result } = renderHook(() =>
      usePersistedState({
        key: 'test_key',
        parse: (raw) => (raw ? JSON.parse(raw) : 0),
        serialize: (v) => JSON.stringify(v),
        fallback: 0,
      }),
    );

    await act(async () => {
      await Promise.resolve();
    });

    expect(result.current.value).toBe(0);
    expect(result.current.isLoading).toBe(false);

    errorSpy.mockRestore();
    getItemSpy.mockRestore();
  });
});

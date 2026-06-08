import { describe, it, expect, jest, beforeEach, afterEach } from '@jest/globals';
import { Platform } from 'react-native';

jest.mock('expo-speech-recognition', () => {
  const listeners: Record<string, Set<(event: unknown) => void>> = {};
  const ExpoSpeechRecognitionModule = {
    addListener: jest.fn((event: string, cb: (event: unknown) => void) => {
      (listeners[event] ||= new Set()).add(cb);
      return {
        remove: () => {
          listeners[event]?.delete(cb);
        },
      };
    }),
    start: jest.fn(),
    stop: jest.fn(),
    abort: jest.fn(),
    isRecognitionAvailable: jest.fn(() => true),
    getPermissionsAsync: jest.fn(async () => ({ granted: true })),
    requestPermissionsAsync: jest.fn(async () => ({ granted: true })),
    __emit: (event: string, payload?: unknown) => {
      for (const listener of Array.from(listeners[event] ?? [])) {
        listener(payload);
      }
    },
    __listenerCount: () =>
      Object.values(listeners).reduce((count, eventListeners) => count + eventListeners.size, 0),
    __resetListeners: () => {
      for (const key of Object.keys(listeners)) {
        listeners[key]?.clear();
      }
    },
  };
  return { ExpoSpeechRecognitionModule };
});

// eslint-disable-next-line import/first
import { ExpoSttService } from '../sttExpo';
// eslint-disable-next-line import/first
import { ExpoSpeechRecognitionModule } from 'expo-speech-recognition';

const mod = ExpoSpeechRecognitionModule as unknown as {
  start: jest.Mock;
  stop: jest.Mock;
  abort: jest.Mock;
  isRecognitionAvailable: jest.Mock<() => boolean>;
  getPermissionsAsync: jest.Mock<() => Promise<{ granted: boolean }>>;
  requestPermissionsAsync: jest.Mock<() => Promise<{ granted: boolean }>>;
  __emit: (event: string, payload?: unknown) => void;
  __listenerCount: () => number;
  __resetListeners: () => void;
};

const originalPlatformOS = Platform.OS;
const originalPlatformVersion = Platform.Version;

function resultEvent(transcript: string, isFinal: boolean) {
  return {
    isFinal,
    results: [{ transcript, confidence: 1, segments: [] }],
  };
}

async function flushStart() {
  await Promise.resolve();
  await Promise.resolve();
}

function setPlatform(os: typeof Platform.OS, version: typeof Platform.Version) {
  Object.defineProperty(Platform, 'OS', { configurable: true, get: () => os });
  Object.defineProperty(Platform, 'Version', { configurable: true, get: () => version });
}

describe('ExpoSttService', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    setPlatform('ios', '17.0');
    mod.__resetListeners();
    mod.start.mockReset();
    mod.stop.mockReset();
    mod.abort.mockReset();
    mod.isRecognitionAvailable.mockReset().mockReturnValue(true);
    mod.getPermissionsAsync.mockReset().mockResolvedValue({ granted: true });
    mod.requestPermissionsAsync.mockReset().mockResolvedValue({ granted: true });
    jest.spyOn(console, 'warn').mockImplementation(() => {});
  });

  afterEach(() => {
    setPlatform(originalPlatformOS, originalPlatformVersion);
    jest.useRealTimers();
    jest.restoreAllMocks();
  });

  it('starts Expo recognition and accumulates multiple final segments', async () => {
    const onPartialResult = jest.fn();
    const onListeningStateChange = jest.fn();
    const service = new ExpoSttService();
    const promise = service.startListening({
      language: 'en-US',
      onPartialResult,
      onListeningStateChange,
    });
    await flushStart();

    expect(mod.start).toHaveBeenCalledWith(
      expect.objectContaining({
        lang: 'en-US',
        interimResults: true,
        continuous: true,
        maxAlternatives: 1,
      }),
    );

    mod.__emit('start');
    mod.__emit('result', resultEvent('hello', true));
    mod.__emit('result', resultEvent('world', true));
    mod.__emit('end');

    await expect(promise).resolves.toBe('hello world');
    expect(onPartialResult).toHaveBeenNthCalledWith(1, 'hello');
    expect(onPartialResult).toHaveBeenLastCalledWith('hello world');
    expect(onListeningStateChange).toHaveBeenNthCalledWith(1, true);
    expect(onListeningStateChange).toHaveBeenLastCalledWith(false);
    expect(mod.__listenerCount()).toBe(0);
  });

  it('dedupes final and interim overlap', async () => {
    const onPartialResult = jest.fn();
    const service = new ExpoSttService();
    const promise = service.startListening({ language: 'en-US', onPartialResult });
    await flushStart();

    mod.__emit('result', resultEvent('hello', true));
    mod.__emit('result', resultEvent('hello world', false));
    mod.__emit('end');

    await expect(promise).resolves.toBe('hello world');
    expect(onPartialResult).toHaveBeenLastCalledWith('hello world');
  });

  it('stops recognition and resolves with the captured partial on hard timeout', async () => {
    const service = new ExpoSttService();
    const promise = service.startListening({ language: 'en-US', timeoutMs: 5000 });
    await flushStart();

    mod.__emit('result', resultEvent('partial answer', false));
    jest.advanceTimersByTime(5000);

    await expect(promise).resolves.toBe('partial answer');
    expect(mod.stop).toHaveBeenCalled();
    expect(mod.__listenerCount()).toBe(0);
  });

  it('tears down a superseded session before starting a new one', async () => {
    const service = new ExpoSttService();

    const first = service.startListening({ language: 'en-US' });
    await flushStart();
    mod.__emit('result', resultEvent('first answer', false));
    expect(mod.__listenerCount()).toBe(5);

    const second = service.startListening({ language: 'pl-PL' });
    await flushStart();

    await expect(first).resolves.toBe('first answer');
    expect(mod.abort).toHaveBeenCalledTimes(1);
    expect(mod.__listenerCount()).toBe(5);

    mod.__emit('result', resultEvent('second answer', true));
    mod.__emit('end');
    await expect(second).resolves.toBe('second answer');
    expect(mod.__listenerCount()).toBe(0);
  });

  it('rejects when speech-recognition permission is denied', async () => {
    mod.getPermissionsAsync.mockResolvedValue({ granted: false });
    mod.requestPermissionsAsync.mockResolvedValue({ granted: false });

    const service = new ExpoSttService();

    await expect(service.startListening({ language: 'en-US' })).rejects.toThrow(
      /permission not granted/i,
    );
    expect(mod.start).not.toHaveBeenCalled();
  });

  it('rejects before start when recognition is unavailable (permissions checked first)', async () => {
    const onListeningStateChange = jest.fn();
    mod.isRecognitionAvailable.mockReturnValue(false);

    const service = new ExpoSttService();

    await expect(
      service.startListening({ language: 'en-US', onListeningStateChange }),
    ).rejects.toThrow(/not supported/i);
    expect(mod.getPermissionsAsync).toHaveBeenCalled();
    expect(mod.start).not.toHaveBeenCalled();
    expect(onListeningStateChange).toHaveBeenCalledWith(false);
  });

  it('grants permission on first request and proceeds to start recognition', async () => {
    mod.getPermissionsAsync.mockResolvedValue({ granted: false });
    mod.requestPermissionsAsync.mockResolvedValue({ granted: true });

    const service = new ExpoSttService();
    const promise = service.startListening({ language: 'en-US' });
    await flushStart();

    expect(mod.getPermissionsAsync).toHaveBeenCalled();
    expect(mod.requestPermissionsAsync).toHaveBeenCalled();
    expect(mod.start).toHaveBeenCalledWith(
      expect.objectContaining({ lang: 'en-US' }),
    );

    mod.__emit('result', resultEvent('hello', true));
    mod.__emit('end');
    await expect(promise).resolves.toBe('hello');
  });

  it('resolves no-speech errors with the captured text', async () => {
    const service = new ExpoSttService();
    const promise = service.startListening({ language: 'en-US' });
    await flushStart();

    mod.__emit('result', resultEvent('captured partial', false));
    mod.__emit('error', { error: 'no-speech', message: 'No speech detected' });

    await expect(promise).resolves.toBe('captured partial');
    expect(mod.__listenerCount()).toBe(0);
  });

  it('rejects service failures so the study flow can show an STT error', async () => {
    const onListeningStateChange = jest.fn();
    const service = new ExpoSttService();
    const promise = service.startListening({ language: 'en-US', onListeningStateChange });
    await flushStart();

    mod.__emit('error', { error: 'network', message: 'Network unavailable' });

    await expect(promise).rejects.toThrow(/network unavailable/i);
    expect(onListeningStateChange).toHaveBeenLastCalledWith(false);
    expect(mod.__listenerCount()).toBe(0);
  });

  it('falls back to non-continuous mode on Android 12 and below', async () => {
    setPlatform('android', 31);
    const service = new ExpoSttService();
    const promise = service.startListening({ language: 'en-US' });
    await flushStart();

    expect(mod.start).toHaveBeenCalledWith(expect.objectContaining({ continuous: false }));

    mod.__emit('end');
    await expect(promise).resolves.toBe('');
  });

  it('clears localStorage flag on web not-allowed error so next start re-prompts', async () => {
    const storage: Record<string, string> = {};
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      writable: true,
      value: {
        getItem: jest.fn((key: string) => storage[key] ?? null),
        setItem: jest.fn((key: string, value: string) => { storage[key] = value; }),
        removeItem: jest.fn((key: string) => { delete storage[key]; }),
      },
    });
    Object.defineProperty(globalThis, 'navigator', {
      configurable: true,
      writable: true,
      value: {
        mediaDevices: {
          getUserMedia: jest.fn(async () => {
            const stream = {
              getTracks: () => [{ stop: jest.fn() }],
            };
            return stream;
          }),
        },
      },
    });

    setPlatform('web', 'any');
    mod.getPermissionsAsync.mockResolvedValue({ granted: true });

    const service = new ExpoSttService();
    const promise = service.startListening({ language: 'en-US' });
    await flushStart();

    mod.__emit('error', { error: 'not-allowed', message: 'Permission denied' });

    await expect(promise).rejects.toThrow(/Permission denied/i);
    expect(localStorage.removeItem).toHaveBeenCalledWith('raycoll_mic_permission_granted');

    delete (globalThis as Record<string, unknown>).localStorage;
    delete (globalThis as Record<string, unknown>).navigator;
  });
});

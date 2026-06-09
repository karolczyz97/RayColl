import * as Speech from 'expo-speech';
import { ttsService } from '../ttsService';

jest.mock('expo-speech', () => ({
  speak: jest.fn(),
  stop: jest.fn(() => Promise.resolve()),
}));

const mockedSpeech = Speech as jest.Mocked<typeof Speech>;

describe('ttsService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockedSpeech.stop.mockResolvedValue(undefined);
  });

  it('speaks through expo-speech on every platform', async () => {
    mockedSpeech.speak.mockImplementation((_text, options) => {
      options?.onDone?.();
    });

    await expect(
      ttsService.speak({ text: 'hello', lang: 'en-US', rate: 0.9 }),
    ).resolves.toBeUndefined();

    expect(mockedSpeech.stop).toHaveBeenCalledTimes(1);
    expect(mockedSpeech.speak).toHaveBeenCalledWith(
      'hello',
      expect.objectContaining({
        language: 'en-US',
        rate: 0.9,
      }),
    );
  });

  it('resolves when speech is stopped', async () => {
    mockedSpeech.speak.mockImplementation((_text, options) => {
      options?.onStopped?.();
    });

    await expect(ttsService.speak({ text: 'stop me', lang: 'en-US' })).resolves.toBeUndefined();
  });

  it('cancels queued speech through expo-speech', () => {
    ttsService.cancel();

    expect(mockedSpeech.stop).toHaveBeenCalledTimes(1);
  });

  // `speak()` awaits `Speech.stop()` before calling `Speech.speak`, so callbacks
  // are only registered on the next microtask. Flush it before triggering them.
  const flushMicrotasks = () => new Promise<void>((resolve) => setImmediate(resolve));

  it('resolves (does not reject) when the active utterance is cancelled mid-playback', async () => {
    // Web/Android report an interruption from cancel() through onError; that must
    // not surface as a playback failure.
    let triggerError: ((e: Error) => void) | undefined;
    mockedSpeech.speak.mockImplementation((_text, options) => {
      triggerError = options?.onError;
    });

    const promise = ttsService.speak({ text: 'interrupt me', lang: 'en-US' });
    await flushMicrotasks();
    ttsService.cancel();
    triggerError?.(new Error('interrupted'));

    await expect(promise).resolves.toBeUndefined();
  });

  it('rejects on a genuine error of the active, non-cancelled utterance', async () => {
    let triggerError: ((e: Error) => void) | undefined;
    mockedSpeech.speak.mockImplementation((_text, options) => {
      triggerError = options?.onError;
    });

    const promise = ttsService.speak({ text: 'fail me', lang: 'en-US' });
    await flushMicrotasks();
    triggerError?.(new Error('synthesis-failed'));

    await expect(promise).rejects.toThrow('synthesis-failed');
  });

  it('resolves a superseded utterance when a newer speak() starts', async () => {
    const errorTriggers: ((e: Error) => void)[] = [];
    let callCount = 0;
    mockedSpeech.speak.mockImplementation((_text, options) => {
      callCount += 1;
      if (options?.onError) errorTriggers.push(options.onError);
      // Auto-complete only the second utterance so the first stays pending and
      // can later be interrupted via onError.
      if (callCount === 2) options?.onDone?.();
    });

    const first = ttsService.speak({ text: 'first', lang: 'en-US' });
    await flushMicrotasks();
    await ttsService.speak({ text: 'second', lang: 'en-US' });
    // The first utterance is interrupted by the second; its late onError must
    // resolve rather than reject.
    errorTriggers[0]?.(new Error('interrupted'));

    await expect(first).resolves.toBeUndefined();
  });
});

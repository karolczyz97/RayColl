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
});

import * as Speech from 'expo-speech';

export interface TtsOptions {
  text: string;
  lang: string;
  rate?: number;
}

class TtsService {
  async speak(options: TtsOptions): Promise<void> {
    await Speech.stop();
    return new Promise((resolve, reject) => {
      Speech.speak(options.text, {
        language: options.lang,
        rate: options.rate ?? 1.0,
        onDone: () => resolve(),
        onStopped: () => resolve(),
        onError: (e) => reject(e),
      });
    });
  }

  cancel(): void {
    void Speech.stop();
  }
}

export const ttsService = new TtsService();

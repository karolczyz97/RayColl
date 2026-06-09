import * as Speech from 'expo-speech';

export interface TtsOptions {
  text: string;
  lang: string;
  rate?: number;
}

class TtsService {
  // Monotonic id of the most recently started utterance.
  private currentUtterance = 0;
  // Highest utterance id (inclusive) that was explicitly cancelled.
  private cancelledThrough = 0;

  async speak(options: TtsOptions): Promise<void> {
    const utteranceId = ++this.currentUtterance;
    await Speech.stop();
    return new Promise<void>((resolve, reject) => {
      Speech.speak(options.text, {
        language: options.lang,
        rate: options.rate ?? 1.0,
        onDone: () => resolve(),
        onStopped: () => resolve(),
        onError: (e) => {
          // An utterance can be *interrupted* rather than failed: either we
          // explicitly cancelled it (skip / stop / leave study), or a newer
          // utterance superseded it. On web, `speechSynthesis.cancel()` reports
          // every interruption through `onerror` (e.g. "interrupted"/"canceled"),
          // and Android emits `speakingError` on stop too. Surfacing those as a
          // playback failure produced a spurious "audio playback failed" error,
          // so treat any interruption as a normal stop and only reject on a
          // genuine failure of the still-active utterance.
          if (this.cancelledThrough >= utteranceId || utteranceId !== this.currentUtterance) {
            resolve();
          } else {
            reject(e);
          }
        },
      });
    });
  }

  cancel(): void {
    this.cancelledThrough = this.currentUtterance;
    void Speech.stop();
  }
}

export const ttsService = new TtsService();

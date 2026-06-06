import { combineRecognizedText, normalizeSttResult, DEFAULT_STT_TIMEOUT_MS, DEFAULT_STT_INACTIVITY_MS, type SttOptions, type SttService } from './sttTypes';
import { createSttSession } from './sttSessionHelpers';
import type { BrowserWindowWith } from '@/utils/types';

interface SpeechRecognitionEvent {
  resultIndex: number;
  results: {
    length: number;
    [index: number]: {
      isFinal: boolean;
      [index: number]: {
        transcript: string;
      };
    };
  };
}

interface SpeechRecognitionErrorEvent {
  error: string;
}

interface ISpeechRecognition {
  lang: string;
  interimResults: boolean;
  maxAlternatives: number;
  continuous: boolean;
  onstart: (() => void) | null;
  onresult: ((event: SpeechRecognitionEvent) => void) | null;
  onend: (() => void) | null;
  onerror: ((event: SpeechRecognitionErrorEvent) => void) | null;
  start(): void;
  stop(): void;
}

type SpeechRecognitionConstructor = new () => ISpeechRecognition;

function getWebSpeechRecognition(): SpeechRecognitionConstructor | undefined {
  if (typeof window === 'undefined') return undefined;
  const speechWindow = window as BrowserWindowWith<'SpeechRecognition' | 'webkitSpeechRecognition', SpeechRecognitionConstructor>;
  return speechWindow.SpeechRecognition || speechWindow.webkitSpeechRecognition;
}

export class WebSttService implements SttService {
  private recognition: ISpeechRecognition | null = null;

  isSupported(): boolean {
    return getWebSpeechRecognition() !== undefined;
  }

  /**
   * Detaches callbacks and stops the currently-attached recognition, if any.
   * Detaching before stop() prevents re-entering our own handlers, and clearing the
   * reference avoids a zombie session holding the microphone after we move on.
   */
  private stopActiveRecognition() {
    const active = this.recognition;
    if (!active) return;
    active.onstart = null;
    active.onresult = null;
    active.onend = null;
    active.onerror = null;
    try {
      active.stop();
    } catch {
      // stop() can throw if recognition never started; safe to ignore.
    }
    this.recognition = null;
  }

  startListening(options: SttOptions): Promise<string> {
    return new Promise((resolve, reject) => {
      const SpeechRec = getWebSpeechRecognition();
      if (!SpeechRec) {
        reject(new Error('STT not supported'));
        return;
      }

      // Kill any previous session still holding the mic before starting a new one.
      this.stopActiveRecognition();

      const recognition = new SpeechRec() as ISpeechRecognition;
      this.recognition = recognition;
      recognition.lang = options.language;
      recognition.interimResults = true;
      recognition.maxAlternatives = 1;
      recognition.continuous = true;

      const finalResults = new Map<number, string>();
      let lastInterimResult = '';

      const getFinalResultText = () =>
        Array.from(finalResults.entries())
          .sort(([left], [right]) => left - right)
          .map(([, text]) => text)
          .join(' ');

      const teardown = () => {
        recognition.onstart = null;
        recognition.onresult = null;
        recognition.onend = null;
        recognition.onerror = null;
        try {
          recognition.stop();
        } catch {
          // Already stopped/ended; ignore.
        }
        // Only clear the shared reference if a newer session hasn't replaced it.
        if (this.recognition === recognition) {
          this.recognition = null;
        }
      };

      const finishWithResult = () => {
        if (session.resolved()) return;
        session.setResolved();
        session.cancelTimers();
        teardown();
        options.onListeningStateChange?.(false);
        resolve(combineRecognizedText(getFinalResultText(), lastInterimResult));
      };

      const session = createSttSession(
        options.timeoutMs || DEFAULT_STT_TIMEOUT_MS,
        DEFAULT_STT_INACTIVITY_MS,
        () => finishWithResult(),
        () => finishWithResult(),
      );

      recognition.onstart = () => options.onListeningStateChange?.(true);

      recognition.onresult = (event: SpeechRecognitionEvent) => {
        const interimParts: string[] = [];
        let hasFinal = false;
        for (let i = event.resultIndex; i < event.results.length; i++) {
          const transcript = normalizeSttResult(event.results[i][0].transcript);
          if (!transcript) {
            continue;
          }

          if (event.results[i].isFinal) {
            finalResults.set(i, transcript);
            hasFinal = true;
          } else {
            interimParts.push(transcript);
          }
        }

        lastInterimResult = interimParts.join(' ');
        options.onPartialResult?.(combineRecognizedText(getFinalResultText(), lastInterimResult));

        if (hasFinal) {
          session.resetInactivity();
        }
      };

      recognition.onend = () => {
        finishWithResult();
      };

      recognition.onerror = (event: SpeechRecognitionErrorEvent) => {
        if (session.resolved()) return;
        session.setResolved();
        session.cancelTimers();
        teardown();
        options.onListeningStateChange?.(false);
        if (event.error === 'no-speech' || event.error === 'aborted') {
          resolve(combineRecognizedText(getFinalResultText(), lastInterimResult));
        } else {
          reject(new Error(event.error));
        }
      };

      recognition.start();
    });
  }

  async stopListening(): Promise<void> {
    this.recognition?.stop();
  }
}

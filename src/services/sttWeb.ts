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

  startListening(options: SttOptions): Promise<string> {
    return new Promise((resolve, reject) => {
      const SpeechRec = getWebSpeechRecognition();
      if (!SpeechRec) {
        reject(new Error('STT not supported'));
        return;
      }

      this.recognition = new SpeechRec() as ISpeechRecognition;
      this.recognition.lang = options.language;
      this.recognition.interimResults = true;
      this.recognition.maxAlternatives = 1;
      this.recognition.continuous = true;

      const finalResults = new Map<number, string>();
      let lastInterimResult = '';

      const getFinalResultText = () =>
        Array.from(finalResults.entries())
          .sort(([left], [right]) => left - right)
          .map(([, text]) => text)
          .join(' ');

      const finishWithResult = () => {
        if (session.resolved()) return;
        session.setResolved();
        session.cancelTimers();
        options.onListeningStateChange?.(false);
        resolve(combineRecognizedText(getFinalResultText(), lastInterimResult));
      };

      const session = createSttSession(
        options.timeoutMs || DEFAULT_STT_TIMEOUT_MS,
        DEFAULT_STT_INACTIVITY_MS,
        () => finishWithResult(),
        () => finishWithResult(),
      );

      this.recognition.onstart = () => options.onListeningStateChange?.(true);

      this.recognition.onresult = (event: SpeechRecognitionEvent) => {
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

      this.recognition.onend = () => {
        finishWithResult();
      };

      this.recognition.onerror = (event: SpeechRecognitionErrorEvent) => {
        if (session.resolved()) return;
        session.cancelTimers();
        session.setResolved();
        options.onListeningStateChange?.(false);
        if (event.error === 'no-speech' || event.error === 'aborted') {
          resolve(combineRecognizedText(getFinalResultText(), lastInterimResult));
        } else {
          reject(new Error(event.error));
        }
      };

      this.recognition.start();
    });
  }

  async stopListening(): Promise<void> {
    this.recognition?.stop();
  }
}

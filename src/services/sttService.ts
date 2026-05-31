import { Platform } from 'react-native';
import Voice from '@react-native-voice/voice';
import { getErrorMessage } from '../utils/errors';

export interface SttOptions {
  language: string;
  timeoutMs?: number;
  onPartialResult?: (text: string) => void;
  onListeningStateChange?: (listening: boolean) => void;
}

export interface SttService {
  startListening(options: SttOptions): Promise<string>;
  stopListening(): Promise<void>;
  isSupported(): boolean;
}

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

type BrowserWindowWithSpeechRecognition = Window &
  typeof globalThis & {
    SpeechRecognition?: SpeechRecognitionConstructor;
    webkitSpeechRecognition?: SpeechRecognitionConstructor;
  };

function getWebSpeechRecognition(): SpeechRecognitionConstructor | undefined {
  if (typeof window === 'undefined') return undefined;
  const speechWindow = window as BrowserWindowWithSpeechRecognition;
  return speechWindow.SpeechRecognition || speechWindow.webkitSpeechRecognition;
}

function normalizeRecognizedText(text: string): string {
  return text.replace(/\s+/g, ' ').trim();
}

function combineRecognizedText(finalText: string, interimText: string): string {
  const normalizedFinal = normalizeRecognizedText(finalText);
  const normalizedInterim = normalizeRecognizedText(interimText);

  if (!normalizedFinal) return normalizedInterim;
  if (!normalizedInterim) return normalizedFinal;

  const finalLower = normalizedFinal.toLocaleLowerCase();
  const interimLower = normalizedInterim.toLocaleLowerCase();

  if (interimLower === finalLower || interimLower.startsWith(finalLower)) {
    return normalizedInterim;
  }
  if (finalLower.endsWith(interimLower)) {
    return normalizedFinal;
  }

  return `${normalizedFinal} ${normalizedInterim}`;
}

// Web browser STT using SpeechRecognition API
class WebSttService implements SttService {
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
      let resolved = false;
      let inactivityTimer: ReturnType<typeof setTimeout> | null = null;
      const INACTIVITY_MS = 1500; // 1.5s inactivity timeout

      const hardTimeout = setTimeout(() => {
        if (!resolved) this.recognition?.stop();
      }, options.timeoutMs || 15000);

      const finishWithResult = () => {
        if (resolved) return;
        resolved = true;
        clearTimeout(hardTimeout);
        if (inactivityTimer) clearTimeout(inactivityTimer);
        options.onListeningStateChange?.(false);
        resolve(combineRecognizedText(getFinalResultText(), lastInterimResult));
      };

      const getFinalResultText = () =>
        Array.from(finalResults.entries())
          .sort(([left], [right]) => left - right)
          .map(([, text]) => text)
          .join(' ');

      const resetInactivityTimer = () => {
        if (inactivityTimer) clearTimeout(inactivityTimer);
        inactivityTimer = setTimeout(() => {
          this.recognition?.stop();
        }, INACTIVITY_MS);
      };

      this.recognition.onstart = () => options.onListeningStateChange?.(true);

      this.recognition.onresult = (event: SpeechRecognitionEvent) => {
        const interimParts: string[] = [];
        let hasFinal = false;
        for (let i = event.resultIndex; i < event.results.length; i++) {
          const transcript = normalizeRecognizedText(event.results[i][0].transcript);
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
          resetInactivityTimer();
        }
      };

      this.recognition.onend = () => {
        finishWithResult();
      };

      this.recognition.onerror = (event: SpeechRecognitionErrorEvent) => {
        if (resolved) return;
        clearTimeout(hardTimeout);
        if (inactivityTimer) clearTimeout(inactivityTimer);
        resolved = true;
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

// Native STT using @react-native-voice/voice
class ReactNativeVoiceSttService implements SttService {
  isSupported(): boolean {
    return Platform.OS !== 'web';
  }

  startListening(options: SttOptions): Promise<string> {
    return new Promise((resolve, reject) => {
      let finalResult = '';
      let resolved = false;
      let inactivityTimer: ReturnType<typeof setTimeout> | null = null;
      const INACTIVITY_MS = 1500; // 1.5s inactivity timeout

      const hardTimeout = setTimeout(async () => {
        if (!resolved) {
          try {
            await Voice.stop();
          } catch (err) {
            console.warn('Failed to stop voice recognition after timeout:', getErrorMessage(err));
          }
        }
      }, options.timeoutMs || 15000);

      const cleanup = async () => {
        clearTimeout(hardTimeout);
        if (inactivityTimer) clearTimeout(inactivityTimer);
        Voice.onSpeechStart = () => {};
        Voice.onSpeechEnd = () => {};
        Voice.onSpeechResults = () => {};
        Voice.onSpeechPartialResults = () => {};
        Voice.onSpeechError = () => {};
      };

      const finishWithResult = async (result: string) => {
        if (resolved) return;
        resolved = true;
        await cleanup();
        options.onListeningStateChange?.(false);
        resolve(result);
      };

      const resetInactivityTimer = () => {
        if (inactivityTimer) clearTimeout(inactivityTimer);
        inactivityTimer = setTimeout(async () => {
          try {
            await Voice.stop();
          } catch (err) {
            console.warn('Failed to stop voice recognition after inactivity:', getErrorMessage(err));
          }
        }, INACTIVITY_MS);
      };

      Voice.onSpeechStart = () => {
        options.onListeningStateChange?.(true);
        resetInactivityTimer();
      };

      Voice.onSpeechEnd = () => {
        finishWithResult(finalResult);
      };

      Voice.onSpeechResults = (e) => {
        if (e.value && e.value.length > 0) {
          finalResult = normalizeRecognizedText(e.value[0]);
          options.onPartialResult?.(finalResult);
        }
        resetInactivityTimer();
      };

      Voice.onSpeechPartialResults = (e) => {
        if (e.value && e.value.length > 0) {
          finalResult = normalizeRecognizedText(e.value[0]);
          options.onPartialResult?.(finalResult);
        }
        resetInactivityTimer();
      };

      Voice.onSpeechError = (e) => {
        if (resolved) return;
        if (e.error) {
          console.warn('Speech recognition error:', e.error.message || e.error.code || 'unknown');
        }
        cleanup();
        resolved = true;
        options.onListeningStateChange?.(false);
        resolve(finalResult || '');
      };

      Voice.start(options.language).catch((err) => {
        cleanup();
        options.onListeningStateChange?.(false);
        reject(err);
      });
    });
  }

  async stopListening(): Promise<void> {
    try {
      await Voice.stop();
    } catch (err) {
      console.warn('Failed to stop voice recognition:', getErrorMessage(err));
    }
  }
}

export function getSttService(): SttService {
  if (Platform.OS === 'web') {
    return new WebSttService();
  }
  return new ReactNativeVoiceSttService();
}

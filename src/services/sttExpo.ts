import { Platform } from 'react-native';
import {
  ExpoSpeechRecognitionModule,
  type ExpoSpeechRecognitionErrorEvent,
  type ExpoSpeechRecognitionResultEvent,
} from 'expo-speech-recognition';
import { getErrorMessage } from '@/utils/errors';
import {
  DEFAULT_STT_INACTIVITY_MS,
  DEFAULT_STT_TIMEOUT_MS,
  type SttOptions,
  type SttService,
} from './sttTypes';
import { createSttSession } from './sttSessionHelpers';
import { createSttTranscriptAccumulator } from './sttTranscriptAccumulator';

type RemovableSubscription = { remove: () => void };

const RESOLVABLE_RECOGNITION_ERRORS = new Set<string>([
  'aborted',
  'no-speech',
  'speech-timeout',
]);

/**
 * Cross-platform speech-to-text backed by `expo-speech-recognition`.
 *
 * Expo gives us one event API across native and web, but it does not decide how
 * to merge continuous final/interim segments. The accumulator keeps that policy
 * in our code so web and native both preserve long answers instead of overwriting
 * the previous segment with the latest result.
 */
export class ExpoSttService implements SttService {
  private finishActiveSession: (() => void) | null = null;

  isSupported(): boolean {
    try {
      return ExpoSpeechRecognitionModule.isRecognitionAvailable();
    } catch {
      return false;
    }
  }

  async startListening(options: SttOptions): Promise<string> {
    this.finishAndAbortActiveSession();

    try {
      await ensurePermissions();
    } catch (err) {
      options.onListeningStateChange?.(false);
      throw err;
    }

    if (!this.isSupported()) {
      options.onListeningStateChange?.(false);
      throw new Error('STT not supported');
    }

    return new Promise<string>((resolve, reject) => {
      const accumulator = createSttTranscriptAccumulator();
      const subscriptions: RemovableSubscription[] = [];

      const cleanup = () => {
        session.cancelTimers();
        for (const sub of subscriptions) sub.remove();
        subscriptions.length = 0;
        if (this.finishActiveSession === forceFinish) {
          this.finishActiveSession = null;
        }
      };

      const finishWithResult = () => {
        if (session.resolved()) return;
        session.setResolved();
        const result = accumulator.getText();
        cleanup();
        options.onListeningStateChange?.(false);
        resolve(result);
      };

      const failWithError = (err: Error) => {
        if (session.resolved()) return;
        session.setResolved();
        cleanup();
        options.onListeningStateChange?.(false);
        reject(err);
      };

      const forceFinish = () => finishWithResult();
      this.finishActiveSession = forceFinish;

      const stopRecognition = () => {
        try {
          ExpoSpeechRecognitionModule.stop();
        } catch (err) {
          console.warn('Failed to stop speech recognition:', getErrorMessage(err));
        }
      };

      const session = createSttSession(
        options.timeoutMs || DEFAULT_STT_TIMEOUT_MS,
        DEFAULT_STT_INACTIVITY_MS,
        () => {
          stopRecognition();
          finishWithResult();
        },
        () => {
          stopRecognition();
          finishWithResult();
        },
      );

      subscriptions.push(
        ExpoSpeechRecognitionModule.addListener('start', () => {
          options.onListeningStateChange?.(true);
        }),
        ExpoSpeechRecognitionModule.addListener('result', (event) => {
          const text = getPrimaryTranscript(event);
          if (!text) return;

          const accumulatedText = event.isFinal
            ? accumulator.addFinal(text)
            : accumulator.setInterim(text);
          options.onPartialResult?.(accumulatedText);
          session.resetInactivity();
        }),
        ExpoSpeechRecognitionModule.addListener('nomatch', () => {
          finishWithResult();
        }),
        ExpoSpeechRecognitionModule.addListener('end', () => {
          finishWithResult();
        }),
        ExpoSpeechRecognitionModule.addListener('error', (event) => {
          if (session.resolved()) return;

          if (event.error) {
            console.warn('Speech recognition error:', event.message || event.error);
          }

          if (Platform.OS === 'web' && event.error === 'not-allowed') {
            try {
              localStorage.removeItem('raycoll_mic_permission_granted');
            } catch {
              // localStorage unavailable (private browsing, etc.)
            }
          }

          if (shouldResolveRecognitionError(event)) {
            finishWithResult();
          } else {
            failWithError(new Error(event.message || event.error));
          }
        }),
      );

      try {
        ExpoSpeechRecognitionModule.start({
          lang: options.language,
          interimResults: true,
          continuous: shouldUseContinuousRecognition(),
          maxAlternatives: 1,
        });
      } catch (err) {
        failWithError(err instanceof Error ? err : new Error(getErrorMessage(err)));
      }
    });
  }

  async stopListening(): Promise<void> {
    try {
      ExpoSpeechRecognitionModule.stop();
    } catch (err) {
      console.warn('Failed to stop speech recognition:', getErrorMessage(err));
    }
  }

  private finishAndAbortActiveSession() {
    if (!this.finishActiveSession) return;
    this.finishActiveSession();
    try {
      ExpoSpeechRecognitionModule.abort();
    } catch {
      // abort() can throw if the engine has already ended; safe to ignore.
    }
  }
}

function getPrimaryTranscript(event: ExpoSpeechRecognitionResultEvent): string {
  return event.results.find((result) => result.transcript.trim())?.transcript ?? '';
}

function shouldResolveRecognitionError(event: ExpoSpeechRecognitionErrorEvent): boolean {
  return RESOLVABLE_RECOGNITION_ERRORS.has(event.error);
}

function shouldUseContinuousRecognition(): boolean {
  if (Platform.OS !== 'android') return true;
  const androidVersion = Number(Platform.Version);
  return !Number.isFinite(androidVersion) || androidVersion > 31;
}

export async function ensureWebMicrophonePermission(): Promise<void> {
  if (Platform.OS !== 'web') return;
  if (typeof navigator === 'undefined' || !navigator.mediaDevices?.getUserMedia) return;

  try {
    if (localStorage.getItem('raycoll_mic_permission_granted') === 'true') return;
  } catch {
    // localStorage unavailable (private browsing, etc.)
  }

  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    stream.getTracks().forEach((track) => track.stop());
    try {
      localStorage.setItem('raycoll_mic_permission_granted', 'true');
    } catch {
      // localStorage unavailable
    }
  } catch {
    // User denied or getUserMedia failed; STT will surface its own error
  }
}

async function ensurePermissions(): Promise<void> {
  if (Platform.OS === 'web') {
    await ensureWebMicrophonePermission();
    return;
  }

  const current = await ExpoSpeechRecognitionModule.getPermissionsAsync();
  if (current.granted) return;
  const requested = await ExpoSpeechRecognitionModule.requestPermissionsAsync();
  if (!requested.granted) {
    throw new Error('Speech recognition permission not granted');
  }
}

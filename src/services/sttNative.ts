import { Platform } from 'react-native';
import Voice from '@react-native-voice/voice';
import { getErrorMessage } from '@/utils/errors';
import { normalizeSttResult, type SttOptions, type SttService } from './sttTypes';

export class ReactNativeVoiceSttService implements SttService {
  isSupported(): boolean {
    return Platform.OS !== 'web';
  }

  startListening(options: SttOptions): Promise<string> {
    return new Promise((resolve, reject) => {
      let finalResult = '';
      let resolved = false;
      let inactivityTimer: ReturnType<typeof setTimeout> | null = null;
      const INACTIVITY_MS = 1500;

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
            console.warn(
              'Failed to stop voice recognition after inactivity:',
              getErrorMessage(err),
            );
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
          finalResult = normalizeSttResult(e.value[0]);
          options.onPartialResult?.(finalResult);
        }
        resetInactivityTimer();
      };

      Voice.onSpeechPartialResults = (e) => {
        if (e.value && e.value.length > 0) {
          finalResult = normalizeSttResult(e.value[0]);
          options.onPartialResult?.(finalResult);
        }
        resetInactivityTimer();
      };

      Voice.onSpeechError = (e) => {
        if (resolved) return;
        if (e.error) {
          console.warn(
            'Speech recognition error:',
            e.error.message || e.error.code || 'unknown',
          );
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

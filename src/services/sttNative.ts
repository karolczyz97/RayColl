import { Platform } from 'react-native';
import Voice from '@react-native-voice/voice';
import { getErrorMessage } from '@/utils/errors';
import { normalizeSttResult, DEFAULT_STT_TIMEOUT_MS, DEFAULT_STT_INACTIVITY_MS, type SttOptions, type SttService } from './sttTypes';
import { createSttSession } from './sttSessionHelpers';

export class ReactNativeVoiceSttService implements SttService {
  isSupported(): boolean {
    return Platform.OS !== 'web';
  }

  startListening(options: SttOptions): Promise<string> {
    return new Promise((resolve, reject) => {
      let finalResult = '';

      const cleanup = async () => {
        session.cancelTimers();
        Voice.onSpeechStart = () => {};
        Voice.onSpeechEnd = () => {};
        Voice.onSpeechResults = () => {};
        Voice.onSpeechPartialResults = () => {};
        Voice.onSpeechError = () => {};
      };

      const finishWithResult = async (result: string) => {
        if (session.resolved()) return;
        session.setResolved();
        await cleanup();
        options.onListeningStateChange?.(false);
        resolve(result);
      };

      const session = createSttSession(
        options.timeoutMs || DEFAULT_STT_TIMEOUT_MS,
        DEFAULT_STT_INACTIVITY_MS,
        async () => {
          try {
            await Voice.stop();
          } catch (err) {
            console.warn('Failed to stop voice recognition after timeout:', getErrorMessage(err));
          }
          await finishWithResult(finalResult);
        },
        async () => {
          try {
            await Voice.stop();
          } catch (err) {
            console.warn('Failed to stop voice recognition after inactivity:', getErrorMessage(err));
          }
          await finishWithResult(finalResult);
        },
      );

      Voice.onSpeechStart = () => {
        options.onListeningStateChange?.(true);
        session.resetInactivity();
      };

      Voice.onSpeechEnd = () => {
        finishWithResult(finalResult);
      };

      Voice.onSpeechResults = (e) => {
        if (e.value && e.value.length > 0) {
          finalResult = normalizeSttResult(e.value[0]);
          options.onPartialResult?.(finalResult);
        }
        session.resetInactivity();
      };

      Voice.onSpeechPartialResults = (e) => {
        if (e.value && e.value.length > 0) {
          finalResult = normalizeSttResult(e.value[0]);
          options.onPartialResult?.(finalResult);
        }
        session.resetInactivity();
      };

      Voice.onSpeechError = (e) => {
        if (session.resolved()) return;
        if (e.error) {
          console.warn(
            'Speech recognition error:',
            e.error.message || e.error.code || 'unknown',
          );
        }
        cleanup();
        session.setResolved();
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

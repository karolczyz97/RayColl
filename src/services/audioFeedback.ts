import { createAudioPlayer } from 'expo-audio';
import type { AudioSource } from 'expo-audio';
import { getErrorMessage } from '@/utils/errors';

async function playSound(asset: AudioSource) {
  try {
    const player = createAudioPlayer(asset, { keepAudioSessionActive: true });
    player.play();
    await new Promise<void>((resolve) =>
      setTimeout(() => {
        try {
          player.remove();
        } catch (err: unknown) {
          console.warn('Failed to unload feedback sound:', getErrorMessage(err));
        }
        resolve();
      }, 2000),
    );
  } catch {
    // Feedback sounds are non-critical and can fail with mock assets in tests.
  }
}

export function playMicOnSound(): void {
  playSound(require('@/assets/sounds/mic_on.mp3'));
}

export function playMicOffSound(): void {
  playSound(require('@/assets/sounds/mic_off.mp3'));
}

export function playSuccessSound(): void {
  playSound(require('@/assets/sounds/success.wav'));
}

export function playErrorSound(): void {
  playSound(require('@/assets/sounds/error.wav'));
}

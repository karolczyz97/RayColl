import { Platform } from 'react-native';
import { createAudioPlayer } from 'expo-audio';
import type { AudioSource } from 'expo-audio';
import { getErrorMessage } from '@/utils/errors';

type BrowserWindowWithAudioContext = Window &
  typeof globalThis & {
    webkitAudioContext?: typeof AudioContext;
  };

const getAudioCtx = (() => {
  let ctx: AudioContext | null = null;
  return () => {
    if (Platform.OS === 'web' && !ctx && typeof window !== 'undefined') {
      const audioWindow = window as BrowserWindowWithAudioContext;
      const AudioContextCtor = audioWindow.AudioContext || audioWindow.webkitAudioContext;
      if (!AudioContextCtor) return null;
      ctx = new AudioContextCtor();
    }
    if (ctx?.state === 'suspended') {
      void ctx.resume().catch((err: unknown) => {
        console.warn('Failed to resume audio context:', getErrorMessage(err));
      });
    }
    return ctx;
  };
})();

async function playNativeSound(asset: AudioSource) {
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

function playWebTone(
  frequency: number,
  duration: number,
  gain: number,
  opts?: { delay?: number; type?: OscillatorType },
): void {
  const ctx = getAudioCtx();
  if (!ctx) return;
  const osc = ctx.createOscillator();
  const gainNode = ctx.createGain();
  osc.connect(gainNode);
  gainNode.connect(ctx.destination);
  osc.frequency.value = frequency;
  if (opts?.type) osc.type = opts.type;
  const startTime = ctx.currentTime + (opts?.delay ?? 0);
  gainNode.gain.value = gain;
  osc.start(startTime);
  gainNode.gain.exponentialRampToValueAtTime(0.001, startTime + duration);
  osc.stop(startTime + duration);
}

export function playMicOnSound(): void {
  if (Platform.OS === 'web') {
    playWebTone(880, 0.15, 0.15);
  } else {
    playNativeSound(require('../../assets/sounds/mic_on.mp3'));
  }
}

export function playMicOffSound(): void {
  if (Platform.OS === 'web') {
    playWebTone(440, 0.12, 0.15);
  } else {
    playNativeSound(require('../../assets/sounds/mic_off.mp3'));
  }
}

export function playSuccessSound(): void {
  if (Platform.OS === 'web') {
    playWebTone(660, 0.15, 0.12, { delay: 0 });
    playWebTone(880, 0.15, 0.12, { delay: 0.12 });
  } else {
    playNativeSound(require('../../assets/sounds/success.mp3'));
  }
}

export function playErrorSound(): void {
  if (Platform.OS === 'web') {
    playWebTone(220, 0.2, 0.1, { type: 'square' });
  } else {
    playNativeSound(require('../../assets/sounds/error.mp3'));
  }
}

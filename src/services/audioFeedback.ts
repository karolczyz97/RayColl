import { Platform } from 'react-native';
import { Audio } from 'expo-av';
import type { AVPlaybackSource, AVPlaybackStatus } from 'expo-av';

type BrowserWindowWithAudioContext = Window &
  typeof globalThis & {
    webkitAudioContext?: typeof AudioContext;
  };

function getErrorMessage(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

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

async function playNativeSound(asset: AVPlaybackSource) {
  try {
    const { sound } = await Audio.Sound.createAsync(asset);
    await sound.playAsync();
    sound.setOnPlaybackStatusUpdate((status: AVPlaybackStatus) => {
      if (status.isLoaded && status.didJustFinish) {
        void sound.unloadAsync().catch((err: unknown) => {
          console.warn('Failed to unload feedback sound:', getErrorMessage(err));
        });
      }
    });
  } catch (err: unknown) {
    console.log('Native feedback sound not playable (likely mock asset):', getErrorMessage(err));
  }
}

export function playMicOnSound(): void {
  if (Platform.OS === 'web') {
    const ctx = getAudioCtx();
    if (!ctx) return;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.frequency.value = 880;
    gain.gain.value = 0.15;
    osc.start();
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.15);
    osc.stop(ctx.currentTime + 0.15);
  } else {
    playNativeSound(require('../../assets/sounds/mic_on.mp3'));
  }
}

export function playMicOffSound(): void {
  if (Platform.OS === 'web') {
    const ctx = getAudioCtx();
    if (!ctx) return;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.frequency.value = 440;
    gain.gain.value = 0.15;
    osc.start();
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.12);
    osc.stop(ctx.currentTime + 0.12);
  } else {
    playNativeSound(require('../../assets/sounds/mic_off.mp3'));
  }
}

export function playSuccessSound(): void {
  if (Platform.OS === 'web') {
    const ctx = getAudioCtx();
    if (!ctx) return;
    [660, 880].forEach((freq, i) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.frequency.value = freq;
      gain.gain.value = 0.12;
      osc.start(ctx.currentTime + i * 0.12);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + i * 0.12 + 0.15);
      osc.stop(ctx.currentTime + i * 0.12 + 0.15);
    });
  } else {
    playNativeSound(require('../../assets/sounds/success.mp3'));
  }
}

export function playErrorSound(): void {
  if (Platform.OS === 'web') {
    const ctx = getAudioCtx();
    if (!ctx) return;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.frequency.value = 220;
    osc.type = 'square';
    gain.gain.value = 0.1;
    osc.start();
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.2);
    osc.stop(ctx.currentTime + 0.2);
  } else {
    playNativeSound(require('../../assets/sounds/error.mp3'));
  }
}

import { Platform } from 'react-native';
import { Audio } from 'expo-av';

const getAudioCtx = (() => {
  let ctx: AudioContext | null = null;
  return () => {
    if (Platform.OS === 'web' && !ctx && typeof window !== 'undefined') {
      ctx = new (window.AudioContext || (window as any).webkitAudioContext)();
    }
    if (ctx?.state === 'suspended') ctx.resume();
    return ctx;
  };
})();

async function playNativeSound(asset: any) {
  try {
    const { sound } = await Audio.Sound.createAsync(asset);
    await sound.playAsync();
    sound.setOnPlaybackStatusUpdate((status: any) => {
      if (status.didJustFinish) {
        sound.unloadAsync().catch(() => {});
      }
    });
  } catch (err) {
    console.warn('Failed to play native feedback sound:', err);
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

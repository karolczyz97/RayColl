import { Platform } from 'react-native';
import * as Speech from 'expo-speech';

export interface TtsOptions {
  text: string;
  lang: string;
  rate?: number;
}

function rankVoice(voice: SpeechSynthesisVoice): number {
  const name = voice.name.toLowerCase();
  if (name.includes('wavenet')) return 100;
  if (name.includes('neural')) return 90;
  if (name.includes('google')) return 80;
  if (name.includes('microsoft')) return 70;
  if (!voice.localService) return 60;
  return 50;
}

class TtsService {
  private synth: SpeechSynthesis | null = null;

  constructor() {
    if (Platform.OS === 'web' && typeof window !== 'undefined' && 'speechSynthesis' in window) {
      this.synth = window.speechSynthesis;
    }
  }

  private getBestVoice(lang: string): SpeechSynthesisVoice | null {
    if (!this.synth) return null;
    const voices = this.synth.getVoices();
    const langPrefix = lang.split('-')[0].toLowerCase();
    const matching = voices.filter((v) => {
      const vLang = v.lang.toLowerCase();
      return vLang === lang.toLowerCase() || vLang.startsWith(langPrefix);
    });
    if (matching.length === 0) return null;
    matching.sort((a, b) => rankVoice(b) - rankVoice(a));
    return matching[0];
  }

  speak(options: TtsOptions): Promise<void> {
    return new Promise((resolve, reject) => {
      if (Platform.OS === 'web') {
        if (!this.synth) {
          console.warn('[TTS] SpeechSynthesis unavailable');
          resolve();
          return;
        }
        this.synth.cancel();
        const utterance = new SpeechSynthesisUtterance(options.text);
        utterance.lang = options.lang;
        utterance.rate = options.rate ?? 1.0;
        const voice = this.getBestVoice(options.lang);
        if (voice) utterance.voice = voice;
        utterance.onend = () => resolve();
        utterance.onerror = (e) => {
          if (e.error === 'canceled' || e.error === 'interrupted') resolve();
          else reject(e);
        };
        this.synth.speak(utterance);
      } else {
        // Native (expo-speech)
        Speech.speak(options.text, {
          language: options.lang,
          rate: options.rate ?? 1.0,
          onDone: () => resolve(),
          onError: (e) => reject(e),
        });
      }
    });
  }

  cancel(): void {
    if (Platform.OS === 'web') {
      this.synth?.cancel();
    } else {
      Speech.stop();
    }
  }
}

export const ttsService = new TtsService();

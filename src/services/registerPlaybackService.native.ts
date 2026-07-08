import { Platform } from 'react-native';
import TrackPlayer from 'react-native-track-player';
import { playbackService } from '@/services/mediaSession';

// Efekt uboczny modułu, importowany z index.js zaraz po expo-router/entry —
// RNTP wymaga rejestracji headless serwisu przy starcie (także gdy proces
// budzi FGS po ubiciu aplikacji).
if (Platform.OS === 'android') {
  TrackPlayer.registerPlaybackService(() => playbackService);
}

import { Platform } from 'react-native';

if (Platform.OS === 'android') {
  const TrackPlayer = require('react-native-track-player').default;
  const { playbackService } = require('./src/services/mediaSession');
  TrackPlayer.registerPlaybackService(() => playbackService);
}

import 'expo-router/entry';

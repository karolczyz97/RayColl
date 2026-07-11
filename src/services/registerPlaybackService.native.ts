import { Platform } from 'react-native';
import TrackPlayer, { Event, type BackgroundEvent } from '@rntp/player';
import { handlePlaybackEvent } from '@/services/mediaSession';

// Efekt uboczny modułu, importowany z index.js zaraz po expo-router/entry —
// @rntp/player wymaga rejestracji przy starcie bundla:
// - background handler (Android budzi headless JS z eventami, gdy UI jest w tle),
// - foreground listenery (addEventListener nie dostaje eventów w tle na Androidzie,
//   background handler nie działa w foregroundzie — potrzebne są oba tory).
if (Platform.OS === 'android') {
  TrackPlayer.registerBackgroundEventHandler(() => async (event: BackgroundEvent) => {
    handlePlaybackEvent(event);
  });

  const remoteEvents = [
    Event.RemotePlay,
    Event.RemotePause,
    Event.RemoteNext,
    Event.RemotePrevious,
    Event.RemoteStop,
  ] as const;
  for (const type of remoteEvents) {
    TrackPlayer.addEventListener(type, () => handlePlaybackEvent({ type }));
  }
  TrackPlayer.addEventListener(Event.IsPlayingChanged, ({ playing }) =>
    handlePlaybackEvent({ type: Event.IsPlayingChanged, playing }),
  );
}

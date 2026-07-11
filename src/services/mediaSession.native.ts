/* eslint-disable @typescript-eslint/no-require-imports */
import { Platform } from 'react-native';
import { Event, PlayerCommand, RepeatMode } from '@rntp/player';
import type { SessionEngine } from '@/features/study/session/SessionEngine';
import { getErrorMessage } from '@/utils/errors';

type PlaybackState = 'playing' | 'paused' | 'stopped';

type TrackPlayerType = typeof import('@rntp/player').default;

/** Zdarzenie playbacku (foreground listener lub background handler Androida). */
export type PlaybackEvent = { type: string; playing?: boolean };

class AudioSessionManager {
  private engine: SessionEngine | null = null;
  private state: PlaybackState = 'stopped';
  private initialized = false;
  private groupName = '';
  private pausedTitle = '';

  private get isActive(): boolean {
    return !!this.engine && this.engine.isBackgroundMode();
  }

  private async withTrackPlayer(action: (tp: TrackPlayerType) => Promise<void> | void): Promise<void> {
    if (Platform.OS !== 'android') return;
    try {
      const tp = require('@rntp/player').default;
      await action(tp);
    } catch (err) {
      // Nie wywalaj sesji nauki przez błąd warstwy medialnej, ale zostaw ślad —
      // cichy catch ukrywałby np. nieudany setupPlayer (tło "niby działa").
      console.warn('AudioSessionManager:', getErrorMessage(err));
    }
  }

  async activate(engine: SessionEngine, groupName: string, pausedTitle: string): Promise<void> {
    if (Platform.OS !== 'android') return;
    this.engine = engine;
    this.groupName = groupName;
    this.pausedTitle = pausedTitle;
    engine.setOnBackgroundPause(() => {
      this.setPausedState();
    });

    if (!this.initialized) {
      await this.setupTrackPlayer();
      this.initialized = true;
    }

    if (this.engine !== engine) return;

    await this.startSilentPlayback();
  }

  async deactivate(): Promise<void> {
    if (Platform.OS !== 'android') return;
    this.engine?.setOnBackgroundPause(null);
    this.engine = null;

    if (this.initialized) {
      await this.withTrackPlayer((tp) => {
        tp.stop();
        tp.clear();
      });
      this.state = 'stopped';
    }
  }

  updateNowPlaying(cardFront: string, currentIndex: number, total: number): void {
    if (!this.initialized) return;
    void this.withTrackPlayer((tp) => {
      tp.updateMetadata(0, {
        title: cardFront || '—',
        artist: this.groupName,
        albumTitle: `${currentIndex + 1}/${total}`,
      });
    });
  }

  getCurrentEngine(): SessionEngine | null {
    return this.engine;
  }

  // ---- Called by playback event routing (foreground listeners / background handler) ----

  handleRemotePlay(): void {
    this.state = 'playing';
    if (this.isActive && this.engine) {
      this.engine.resume();
      void this.withTrackPlayer((tp) => {
        tp.play();
      });
    }
  }

  handleRemotePause(): void {
    this.state = 'paused';
    if (this.isActive && this.engine) {
      this.engine.pause();
      void this.withTrackPlayer((tp) => {
        tp.pause();
      });
    }
  }

  handleRemoteNext(): void {
    if (this.isActive) {
      this.engine?.skipToNextCard();
    }
  }

  handleRemotePrevious(): void {
    if (this.isActive) {
      this.engine?.goToPreviousCard();
    }
  }

  handleRemoteStop(): void {
    this.state = 'stopped';
    if (this.isActive) {
      this.engine?.pause();
    }
    void this.deactivate();
  }

  // Media3 zarządza audio focus natywnie (brak zdarzenia RemoteDuck z v4):
  // systemowa pauza/wznowienie (połączenie, inna appka) przychodzi jako zmiana
  // isPlaying — dosynchronizuj silnik sesji z faktycznym stanem playera.
  handleIsPlayingChanged(playing: boolean): void {
    if (!this.isActive) return;
    if (!playing && this.state === 'playing') {
      this.engine?.pause();
      this.state = 'paused';
    } else if (playing && this.state === 'paused') {
      this.engine?.resume();
      this.state = 'playing';
    }
  }

  // ---- Private ----

  private async setupTrackPlayer(): Promise<void> {
    await this.withTrackPlayer((tp) => {
      tp.setupPlayer({
        android: {
          // Ubicie aplikacji (swipe z recents) zabija silnik sesji razem z JS —
          // notyfikacja bez silnika sterowałaby niczym, więc znika razem z FGS.
          taskRemovedBehavior: 'stop',
        },
      });

      tp.setCommands({
        capabilities: [
          PlayerCommand.PlayPause,
          PlayerCommand.Next,
          PlayerCommand.Previous,
          PlayerCommand.Stop,
        ],
        // Przyciski notyfikacji sterują sesją nauki (fiszki), nie kolejką
        // playera — wszystkie komendy mają trafiać do JS zamiast do media3.
        handling: 'js',
      });
    });
  }

  private async startSilentPlayback(): Promise<void> {
    if (this.state === 'playing') return;

    const noiseAsset = require('../../assets/noise-60db.wav');

    await this.withTrackPlayer((tp) => {
      tp.setMediaItems([
        {
          mediaId: 'silent-noise',
          url: noiseAsset,
          title: ' ',
          artist: this.groupName,
        },
      ]);
      tp.setRepeatMode(RepeatMode.One);
      tp.play();
    });
    this.state = 'playing';
  }

  private setPausedState(): void {
    if (!this.initialized) return;
    void this.withTrackPlayer((tp) => {
      tp.updateMetadata(0, {
        title: this.pausedTitle,
        artist: this.groupName,
        albumTitle: '',
      });
      tp.pause();
      this.state = 'paused';
    });
  }
}

export const audioSessionManager = new AudioSessionManager();

/**
 * Routing zdarzeń playbacku do menedżera sesji. Wołane z dwóch źródeł
 * (rejestrowanych w registerPlaybackService.native.ts):
 * - foreground: addEventListener,
 * - background (Android): registerBackgroundEventHandler (headless JS).
 */
export function handlePlaybackEvent(event: PlaybackEvent): void {
  switch (event.type) {
    case Event.RemotePlay:
      audioSessionManager.handleRemotePlay();
      break;
    case Event.RemotePause:
      audioSessionManager.handleRemotePause();
      break;
    case Event.RemoteNext:
      audioSessionManager.handleRemoteNext();
      break;
    case Event.RemotePrevious:
      audioSessionManager.handleRemotePrevious();
      break;
    case Event.RemoteStop:
      audioSessionManager.handleRemoteStop();
      break;
    case Event.IsPlayingChanged:
      audioSessionManager.handleIsPlayingChanged(event.playing === true);
      break;
    default:
      break;
  }
}

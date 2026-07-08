/* eslint-disable @typescript-eslint/no-require-imports */
import { Platform } from 'react-native';
import type { SessionEngine } from '@/features/study/session/SessionEngine';
import { getErrorMessage } from '@/utils/errors';

type PlaybackState = 'playing' | 'paused' | 'stopped';

interface NowPlayingMetadata {
  title: string;
  artist: string;
  album: string;
}

type TrackPlayerType = typeof import('react-native-track-player').default;

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
      const tp = require('react-native-track-player').default;
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
      await this.withTrackPlayer(async (tp) => {
        await tp.reset();
      });
      this.state = 'stopped';
    }
  }

  updateNowPlaying(cardFront: string, currentIndex: number, total: number): void {
    if (!this.initialized) return;
    void this.withTrackPlayer(async (tp) => {
      const metadata: NowPlayingMetadata = {
        title: cardFront || '—',
        artist: this.groupName,
        album: `${currentIndex + 1}/${total}`,
      };
      await tp.updateNowPlayingMetadata(metadata);
    });
  }

  getCurrentEngine(): SessionEngine | null {
    return this.engine;
  }

  // ---- Called by Headless JS PlaybackService ----

  handleRemotePlay(): void {
    this.state = 'playing';
    if (this.isActive && this.engine) {
      this.engine.resume();
      void this.withTrackPlayer(async (tp) => {
        await tp.play();
      });
    }
  }

  handleRemotePause(): void {
    this.state = 'paused';
    if (this.isActive && this.engine) {
      this.engine.pause();
      void this.withTrackPlayer(async (tp) => {
        await tp.pause();
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

  handleRemoteDuck(paused: boolean, permanent: boolean): void {
    if (!this.isActive) return;
    if (permanent || paused) {
      this.engine?.pause();
      void this.withTrackPlayer(async (tp) => {
        await tp.pause();
      });
      this.state = 'paused';
    } else {
      this.handleRemotePlay();
    }
  }

  // ---- Private ----

  private async setupTrackPlayer(): Promise<void> {
    await this.withTrackPlayer(async (tp) => {
      const { Capability } = require('react-native-track-player');
      
      await tp.setupPlayer({
        waitForBuffer: false,
      });

      await tp.updateOptions({
        capabilities: [
          Capability.Play,
          Capability.Pause,
          Capability.SkipToNext,
          Capability.SkipToPrevious,
          Capability.Stop,
        ],
        compactCapabilities: [
          Capability.Play,
          Capability.Pause,
          Capability.SkipToNext,
          Capability.SkipToPrevious,
        ],
        notificationCapabilities: [
          Capability.Play,
          Capability.Pause,
          Capability.SkipToNext,
          Capability.SkipToPrevious,
          Capability.Stop,
        ],
      });
    });
  }

  private async startSilentPlayback(): Promise<void> {
    if (this.state === 'playing') return;

    const noiseAsset = require('../../assets/noise-60db.wav');

    await this.withTrackPlayer(async (tp) => {
      const { RepeatMode } = require('react-native-track-player');
      
      await tp.reset();
      await tp.add([
        {
          id: 'silent-noise',
          url: noiseAsset,
          title: ' ',
          artist: this.groupName,
          album: '',
          duration: 0,
        },
      ]);
      await tp.setRepeatMode(RepeatMode.Track);
      await tp.play();
    });
    this.state = 'playing';
  }

  private setPausedState(): void {
    if (!this.initialized) return;
    void this.withTrackPlayer(async (tp) => {
      await tp.updateNowPlayingMetadata({
        title: this.pausedTitle,
        artist: this.groupName,
        album: '',
      });
      await tp.pause();
      this.state = 'paused';
    });
  }
}

export const audioSessionManager = new AudioSessionManager();

/**
 * Headless JS playback service — registered via TrackPlayer.registerPlaybackService()
 * in the app entry (index.js). Runs in a headless JS context when the app is
 * backgrounded, receiving remote events from lock screen / bluetooth controls.
 */
export async function playbackService(): Promise<void> {
  if (Platform.OS !== 'android') return;

  try {
    const TrackPlayer = require('react-native-track-player').default;
    const { Event } = require('react-native-track-player');

    const handlers: Record<string, () => void> = {
      [Event.RemotePlay]: () => audioSessionManager.handleRemotePlay(),
      [Event.RemotePause]: () => audioSessionManager.handleRemotePause(),
      [Event.RemoteNext]: () => audioSessionManager.handleRemoteNext(),
      [Event.RemotePrevious]: () => audioSessionManager.handleRemotePrevious(),
      [Event.RemoteStop]: () => audioSessionManager.handleRemoteStop(),
    };

    Object.entries(handlers).forEach(([event, handler]) => {
      TrackPlayer.addEventListener(event, handler);
    });

    TrackPlayer.addEventListener(Event.RemoteDuck, (data: { paused: boolean; permanent: boolean }) => {
      audioSessionManager.handleRemoteDuck(data.paused, data.permanent);
    });
  } catch {
    // TrackPlayer not available on this platform — silently no-op.
  }
}

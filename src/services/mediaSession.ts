/* eslint-disable @typescript-eslint/no-require-imports */
import { Platform } from 'react-native';
import type { SessionEngine } from '@/features/study/session/SessionEngine';

type PlaybackState = 'playing' | 'paused' | 'stopped';

interface NowPlayingMetadata {
  title: string;
  artist: string;
  album: string;
}

class AudioSessionManager {
  private engine: SessionEngine | null = null;
  private state: PlaybackState = 'stopped';
  private initialized = false;
  private groupName = '';
  private pausedTitle = '';

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

    await this.startSilentPlayback();
  }

  async deactivate(): Promise<void> {
    if (Platform.OS !== 'android') return;
    this.engine?.setOnBackgroundPause(null);
    this.engine = null;

    try {
      const TrackPlayer = require('react-native-track-player').default;
      await TrackPlayer.reset();
    } catch {
      // TrackPlayer not available (iOS/Web mock)
    }
    this.state = 'stopped';
  }

  updateNowPlaying(cardFront: string, currentIndex: number, total: number): void {
    if (Platform.OS !== 'android' || !this.initialized) return;

    try {
      const TrackPlayer = require('react-native-track-player').default;
      const metadata: NowPlayingMetadata = {
        title: cardFront || '—',
        artist: this.groupName,
        album: `${currentIndex + 1}/${total}`,
      };
      TrackPlayer.updateNowPlayingMetadata(metadata);
    } catch {
      // Silently ignore on no-op platforms
    }
  }

  getCurrentEngine(): SessionEngine | null {
    return this.engine;
  }

  // ---- Called by Headless JS PlaybackService ----

  handleRemotePlay(): void {
    this.state = 'playing';
    if (this.engine && this.engine.isBackgroundMode()) {
      this.engine.resume();
    }
  }

  handleRemotePause(): void {
    this.state = 'paused';
    if (this.engine && this.engine.isBackgroundMode()) {
      this.engine.pause();
    }
  }

  handleRemoteNext(): void {
    if (this.engine && this.engine.isBackgroundMode()) {
      this.engine.skipToNextCard();
    }
  }

  handleRemotePrevious(): void {
    if (this.engine && this.engine.isBackgroundMode()) {
      this.engine.goToPreviousCard();
    }
  }

  handleRemoteStop(): void {
    this.state = 'stopped';
    if (this.engine && this.engine.isBackgroundMode()) {
      this.engine.pause();
    }
    void this.deactivate();
  }

  handleRemoteDuck(paused: boolean, permanent: boolean): void {
    if (!this.engine || !this.engine.isBackgroundMode()) return;
    if (permanent) {
      // Audio focus bezpowrotnie utracony (np. odłączenie słuchawek, inna apka
      // przejęła na stałe) — zatrzymaj sesję i zwolnij FGS.
      this.engine.end();
      return;
    }
    if (paused) {
      this.engine.pause();
    }
  }

  // ---- Private ----

  private async setupTrackPlayer(): Promise<void> {
    const TrackPlayer = require('react-native-track-player').default;
    const { Capability } = require('react-native-track-player');

    await TrackPlayer.setupPlayer({
      waitForBuffer: false,
    });

    await TrackPlayer.updateOptions({
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
  }

  private async startSilentPlayback(): Promise<void> {
    if (this.state === 'playing') return;

    const TrackPlayer = require('react-native-track-player').default;
    const { RepeatMode } = require('react-native-track-player');

    await TrackPlayer.reset();

    // WAV noise at -60dB, generated at build time
    const noiseAsset = require('@/assets/noise-60db.wav');

    await TrackPlayer.add([
      {
        id: 'silent-noise',
        url: noiseAsset,
        title: ' ',
        artist: this.groupName,
        album: '',
        duration: 0,
      },
    ]);

    await TrackPlayer.setRepeatMode(RepeatMode.Track);
    await TrackPlayer.play();
    this.state = 'playing';
  }

  private setPausedState(): void {
    if (Platform.OS !== 'android' || !this.initialized) return;
    try {
      const TrackPlayer = require('react-native-track-player').default;
      TrackPlayer.updateNowPlayingMetadata({
        title: this.pausedTitle,
        artist: this.groupName,
        album: '',
      });
    } catch {
      // no-op on unsupported platforms
    }
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

    TrackPlayer.addEventListener(Event.RemotePlay, () => {
      audioSessionManager.handleRemotePlay();
    });

    TrackPlayer.addEventListener(Event.RemotePause, () => {
      audioSessionManager.handleRemotePause();
    });

    TrackPlayer.addEventListener(Event.RemoteNext, () => {
      audioSessionManager.handleRemoteNext();
    });

    TrackPlayer.addEventListener(Event.RemotePrevious, () => {
      audioSessionManager.handleRemotePrevious();
    });

    TrackPlayer.addEventListener(Event.RemoteStop, () => {
      audioSessionManager.handleRemoteStop();
    });

    TrackPlayer.addEventListener(Event.RemoteDuck, (data: { paused: boolean; permanent: boolean }) => {
      audioSessionManager.handleRemoteDuck(data.paused, data.permanent);
    });
  } catch {
    // TrackPlayer not available on this platform — silently no-op.
  }
}

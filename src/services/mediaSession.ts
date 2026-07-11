import type { SessionEngine } from '@/features/study/session/SessionEngine';

/**
 * Wariant web: brak tła audio i sesji medialnej — pełny no-op o tym samym
 * interfejsie co mediaSession.native.ts. Dzięki temu @rntp/player
 * (i jego peer shaka-player) w ogóle nie trafiają do bundla webowego.
 */
class AudioSessionManager {
  async activate(
    _engine: SessionEngine,
    _groupName: string,
    _pausedTitle: string,
  ): Promise<void> {}

  async deactivate(): Promise<void> {}

  updateNowPlaying(_cardFront: string, _currentIndex: number, _total: number): void {}

  getCurrentEngine(): SessionEngine | null {
    return null;
  }
}

export const audioSessionManager = new AudioSessionManager();

export type PlaybackEvent = { type: string; playing?: boolean };

export function handlePlaybackEvent(_event: PlaybackEvent): void {}

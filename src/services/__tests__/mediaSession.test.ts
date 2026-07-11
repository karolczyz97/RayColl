import { Platform } from 'react-native';
import type { SessionEngine } from '@/features/study/session/SessionEngine';

jest.mock('@rntp/player', () => ({
  __esModule: true,
  default: {
    setupPlayer: jest.fn(),
    setCommands: jest.fn(),
    setMediaItems: jest.fn(),
    setRepeatMode: jest.fn(),
    updateMetadata: jest.fn(),
    play: jest.fn(),
    pause: jest.fn(),
    stop: jest.fn(),
    clear: jest.fn(),
  },
  Event: {
    RemotePlay: 'event.remote-play',
    RemotePause: 'event.remote-pause',
    RemoteNext: 'event.remote-next',
    RemotePrevious: 'event.remote-previous',
    RemoteStop: 'event.remote-stop',
    IsPlayingChanged: 'event.is-playing-changed',
  },
  PlayerCommand: {
    PlayPause: 'playPause',
    Next: 'next',
    Previous: 'previous',
    Stop: 'stop',
  },
  RepeatMode: { Off: 'off', One: 'one', All: 'all' },
}));

jest.mock('../../../assets/noise-60db.wav', () => 1, { virtual: true });

// eslint-disable-next-line import/first -- import po jest.mock, żeby mock objął moduł
import { audioSessionManager, handlePlaybackEvent } from '../mediaSession.native';

// mediaSession jest no-opem poza Androidem — testujemy wariant androidowy.
beforeAll(() => {
  Object.defineProperty(Platform, 'OS', { get: () => 'android' });
});

function createEngineStub(): jest.Mocked<
  Pick<
    SessionEngine,
    | 'isBackgroundMode'
    | 'setOnBackgroundPause'
    | 'resume'
    | 'pause'
    | 'skipToNextCard'
    | 'goToPreviousCard'
  >
> {
  return {
    isBackgroundMode: jest.fn().mockReturnValue(true),
    setOnBackgroundPause: jest.fn(),
    resume: jest.fn(),
    pause: jest.fn(),
    skipToNextCard: jest.fn(),
    goToPreviousCard: jest.fn(),
  };
}

describe('handlePlaybackEvent', () => {
  let engine: ReturnType<typeof createEngineStub>;

  beforeEach(async () => {
    jest.clearAllMocks();
    engine = createEngineStub();
    await audioSessionManager.activate(engine as unknown as SessionEngine, 'Zestaw', 'Pauza');
  });

  afterEach(async () => {
    await audioSessionManager.deactivate();
  });

  it('routes remote-next and remote-previous to card navigation', () => {
    handlePlaybackEvent({ type: 'event.remote-next' });
    expect(engine.skipToNextCard).toHaveBeenCalledTimes(1);

    handlePlaybackEvent({ type: 'event.remote-previous' });
    expect(engine.goToPreviousCard).toHaveBeenCalledTimes(1);
  });

  it('routes remote-pause and remote-play to engine pause/resume', () => {
    handlePlaybackEvent({ type: 'event.remote-pause' });
    expect(engine.pause).toHaveBeenCalledTimes(1);

    handlePlaybackEvent({ type: 'event.remote-play' });
    expect(engine.resume).toHaveBeenCalledTimes(1);
  });

  it('ignores unknown event types', () => {
    handlePlaybackEvent({ type: 'event.playback-error' });
    expect(engine.pause).not.toHaveBeenCalled();
    expect(engine.resume).not.toHaveBeenCalled();
    expect(engine.skipToNextCard).not.toHaveBeenCalled();
  });

  it('does not drive the engine when background mode is off', () => {
    engine.isBackgroundMode.mockReturnValue(false);
    handlePlaybackEvent({ type: 'event.remote-next' });
    handlePlaybackEvent({ type: 'event.remote-pause' });
    expect(engine.skipToNextCard).not.toHaveBeenCalled();
    expect(engine.pause).not.toHaveBeenCalled();
  });

  describe('is-playing-changed (natywna zmiana focus audio — następca RemoteDuck z v4)', () => {
    it('pauses the engine when playback stops while session is playing', () => {
      // activate() kończy w stanie 'playing' (silent playback wystartował)
      handlePlaybackEvent({ type: 'event.is-playing-changed', playing: false });
      expect(engine.pause).toHaveBeenCalledTimes(1);

      // powtórka tego samego stanu nie dubluje pauzy
      handlePlaybackEvent({ type: 'event.is-playing-changed', playing: false });
      expect(engine.pause).toHaveBeenCalledTimes(1);
    });

    it('resumes the engine when playback restarts after a system pause', () => {
      handlePlaybackEvent({ type: 'event.is-playing-changed', playing: false });
      handlePlaybackEvent({ type: 'event.is-playing-changed', playing: true });
      expect(engine.resume).toHaveBeenCalledTimes(1);

      // powtórka nie dubluje wznowienia
      handlePlaybackEvent({ type: 'event.is-playing-changed', playing: true });
      expect(engine.resume).toHaveBeenCalledTimes(1);
    });
  });
});

const ORIGINAL_RELEASE_JSON = process.env.EXPO_PUBLIC_RELEASE_JSON;

function loadReleaseInfo() {
  jest.resetModules();
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  return require('../releaseInfo').releaseInfo as typeof import('../releaseInfo').releaseInfo;
}

describe('releaseInfo', () => {
  afterEach(() => {
    if (ORIGINAL_RELEASE_JSON === undefined) {
      delete process.env.EXPO_PUBLIC_RELEASE_JSON;
    } else {
      process.env.EXPO_PUBLIC_RELEASE_JSON = ORIGINAL_RELEASE_JSON;
    }
    jest.resetModules();
  });

  it('uses the dev release when release metadata is missing', () => {
    delete process.env.EXPO_PUBLIC_RELEASE_JSON;

    const releaseInfo = loadReleaseInfo();

    expect(releaseInfo.webBuild).toBe('dev');
    expect(releaseInfo.commitTitle).toBe('Local development build');
    expect(releaseInfo.notes).toEqual([]);
  });

  it('uses the official release fallback when metadata is missing in production mode', () => {
    delete process.env.EXPO_PUBLIC_RELEASE_JSON;
    const originalDev = (global as any).__DEV__;
    (global as any).__DEV__ = false;

    try {
      const releaseInfo = loadReleaseInfo();

      expect(releaseInfo.webBuild).toBe('production');
      expect(releaseInfo.commitTitle).toBe('Official Release');
      expect(releaseInfo.notes).toEqual([]);
    } finally {
      (global as any).__DEV__ = originalDev;
    }
  });

  it('uses the dev release when release metadata is invalid JSON', () => {
    process.env.EXPO_PUBLIC_RELEASE_JSON = '{bad json';

    const releaseInfo = loadReleaseInfo();

    expect(releaseInfo.webBuild).toBe('dev');
  });

  it('parses valid release metadata and filters non-string notes', () => {
    process.env.EXPO_PUBLIC_RELEASE_JSON = JSON.stringify({
      appVersion: '1.2.3',
      webBuild: 'web-123',
      commitSha: 'abc123',
      commitTitle: 'Release title',
      notes: ['first note', 123, 'second note'],
      publishedAt: '2026-06-05T12:00:00.000Z',
    });

    const releaseInfo = loadReleaseInfo();

    expect(releaseInfo).toEqual({
      appVersion: '1.2.3',
      webBuild: 'web-123',
      commitSha: 'abc123',
      commitTitle: 'Release title',
      notes: ['first note', 'second note'],
      publishedAt: '2026-06-05T12:00:00.000Z',
    });
  });
});

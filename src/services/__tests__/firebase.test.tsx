const mockGetDoc = jest.fn();
const mockDoc = jest.fn((_args: unknown[]) => 'user-root-ref');
const mockLoadUserDataV2 = jest.fn();
const mockMigrateLegacyUserDataToV2 = jest.fn();

jest.mock('firebase/auth', () => ({
  GoogleAuthProvider: Object.assign(jest.fn(), {
    credential: jest.fn(() => 'credential'),
  }),
  signInWithPopup: jest.fn(),
  signInWithCredential: jest.fn(),
  signOut: jest.fn(),
  onAuthStateChanged: jest.fn(),
}));

jest.mock('firebase/firestore', () => ({
  doc: (...args: unknown[]) => mockDoc(args),
  getDoc: (ref: unknown) => mockGetDoc(ref),
}));

jest.mock('expo-auth-session', () => ({
  makeRedirectUri: jest.fn(() => 'raycoll://redirect'),
}));

jest.mock('expo-crypto', () => ({
  randomUUID: jest.fn(() => 'test-uuid'),
}));

jest.mock('expo-web-browser', () => ({
  maybeCompleteAuthSession: jest.fn(),
  openAuthSessionAsync: jest.fn(),
}));

jest.mock('../firebaseClient', () => ({
  auth: null,
  db: {},
}));

jest.mock('@/store/persistence/firestoreV2Persistence', () => ({
  loadUserDataV2: (...args: unknown[]) => mockLoadUserDataV2(...args),
  saveUserDataV2: jest.fn(),
}));

jest.mock('@/store/persistence/firestoreMigration', () => ({
  migrateLegacyUserDataToV2: (...args: unknown[]) => mockMigrateLegacyUserDataToV2(...args),
}));

describe('loadUserData', () => {
  let warnSpy: jest.SpyInstance;

  beforeEach(() => {
    jest.clearAllMocks();
    warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
  });

  afterEach(() => {
    warnSpy.mockRestore();
  });

  it('falls back to V2 data when legacy root data is invalid', async () => {
    const v2Data = { groups: [], studyModes: [], activityHeatmap: {} };
    mockGetDoc.mockResolvedValueOnce({
      exists: () => true,
      data: () => ({
        groups: 'not-an-array',
        studyModes: [],
        activityHeatmap: {},
      }),
    });
    mockLoadUserDataV2.mockResolvedValueOnce(v2Data);

    // eslint-disable-next-line @typescript-eslint/no-require-imports -- Jest runs this suite without ESM VM support.
    const { loadUserData } = require('../firebase');

    await expect(loadUserData('uid-1')).resolves.toBe(v2Data);
    expect(mockMigrateLegacyUserDataToV2).not.toHaveBeenCalled();
    expect(mockLoadUserDataV2).toHaveBeenCalledWith('uid-1');
    expect(warnSpy).toHaveBeenCalledWith(
      'Legacy Firestore data is invalid; falling back to V2 data:',
      'Backup data must contain a "groups" array.',
    );
  });
});

import { signInWithCredential, signOut as fbSignOut, GoogleAuthProvider as MockedGoogleAuthProvider } from 'firebase/auth';
import { signInWithGoogle, signOutUser } from '../firebase';
import { signInWithGoogleNative, signOutGoogleNative } from '../googleNativeAuth';

jest.mock('../firebaseClient', () => ({
  auth: { app: { name: 'mock' } },
  db: null,
}));

jest.mock('@/store/persistence/firestorePersistence', () => ({
  loadUserData: jest.fn(),
  saveUserData: jest.fn(),
}));

jest.mock('firebase/auth', () => ({
  GoogleAuthProvider: {
    credential: jest.fn((idToken: string) => ({ providerId: 'google.com', idToken })),
  },
  signInWithCredential: jest.fn(),
  signInWithPopup: jest.fn(),
  signOut: jest.fn(),
  onAuthStateChanged: jest.fn(),
}));

jest.mock('../googleNativeAuth', () => ({
  signInWithGoogleNative: jest.fn(),
  signOutGoogleNative: jest.fn(),
}));

const mockedSignInWithCredential = signInWithCredential as jest.MockedFunction<typeof signInWithCredential>;
const mockedFbSignOut = fbSignOut as jest.MockedFunction<typeof fbSignOut>;
const mockedSignInNative = signInWithGoogleNative as jest.MockedFunction<typeof signInWithGoogleNative>;
const mockedSignOutNative = signOutGoogleNative as jest.MockedFunction<typeof signOutGoogleNative>;
const mockedGoogleAuthProvider = MockedGoogleAuthProvider as unknown as { credential: jest.Mock };

describe('signInWithGoogle (native path)', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('calls signInWithCredential with credential from idToken', async () => {
    mockedSignInNative.mockResolvedValue('test-id-token');
    mockedSignInWithCredential.mockResolvedValue({
      user: { uid: '123', email: 'test@test.com' },
    } as never);

    const user = await signInWithGoogle();

    expect(user).toEqual({ uid: '123', email: 'test@test.com' });
    expect(mockedGoogleAuthProvider.credential).toHaveBeenCalledWith('test-id-token');
    expect(signInWithCredential).toHaveBeenCalled();
  });

  it('returns null when native sign-in returns null', async () => {
    mockedSignInNative.mockResolvedValue(null);

    const result = await signInWithGoogle();

    expect(result).toBeNull();
    expect(signInWithCredential).not.toHaveBeenCalled();
  });

  it('throws invalid_token when credential exchange fails with auth error', async () => {
    mockedSignInNative.mockResolvedValue('bad-token');
    mockedSignInWithCredential.mockRejectedValue(new Error('INVALID_ID_TOKEN auth error'));

    await expect(signInWithGoogle()).rejects.toThrow('auth.error.invalid_token');
  });
});

describe('signOutUser', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('calls fbSignOut and signOutGoogleNative', async () => {
    mockedFbSignOut.mockResolvedValue(undefined);
    mockedSignOutNative.mockResolvedValue(undefined);

    await signOutUser();

    expect(fbSignOut).toHaveBeenCalled();
    expect(signOutGoogleNative).toHaveBeenCalled();
  });
});

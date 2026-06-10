const mockGoogleSignin = {
  configure: jest.fn(),
  hasPlayServices: jest.fn(),
  signIn: jest.fn(),
  signOut: jest.fn(),
};

jest.mock('@react-native-google-signin/google-signin', () => ({
  GoogleSignin: mockGoogleSignin,
  statusCodes: {
    SIGN_IN_CANCELLED: 'SIGN_IN_CANCELLED',
    IN_PROGRESS: 'IN_PROGRESS',
    PLAY_SERVICES_NOT_AVAILABLE: 'PLAY_SERVICES_NOT_AVAILABLE',
    SIGN_IN_REQUIRED: 'SIGN_IN_REQUIRED',
    NULL_PRESENTER: 'NULL_PRESENTER',
  },
  isErrorWithCode: (err: unknown) => typeof err === 'object' && err !== null && 'code' in err,
  isCancelledResponse: (r: { type: string } | null) => r?.type === 'cancelled',
}));

function loadNativeAuth() {
  // eslint-disable-next-line @typescript-eslint/no-require-imports -- needed for jest.resetModules()
  return require('../googleNativeAuth.native') as {
    signInWithGoogleNative: () => Promise<string | null>;
    signOutGoogleNative: () => Promise<void>;
  };
}

describe('signInWithGoogleNative', () => {
  beforeEach(() => {
    jest.resetModules();
    jest.clearAllMocks();
    delete process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID;
  });

  it('returns idToken on success and configures with webClientId once', async () => {
    process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID = 'test-web-client-id';
    mockGoogleSignin.hasPlayServices.mockResolvedValue(true);
    mockGoogleSignin.signIn.mockResolvedValue({
      type: 'success',
      data: {
        idToken: 'test-id-token',
        user: { id: '1', name: 'Test', email: 'test@test.com', photo: null, familyName: null, givenName: null },
        scopes: [],
        serverAuthCode: null,
      },
    });

    const { signInWithGoogleNative } = loadNativeAuth();
    const token = await signInWithGoogleNative();

    expect(token).toBe('test-id-token');
    expect(mockGoogleSignin.configure).toHaveBeenCalledWith({ webClientId: 'test-web-client-id' });

    mockGoogleSignin.signIn.mockResolvedValue({
      type: 'success',
      data: {
        idToken: 'second-token',
        user: { id: '1', name: 'Test', email: 'test@test.com', photo: null, familyName: null, givenName: null },
        scopes: [],
        serverAuthCode: null,
      },
    });
    const token2 = await signInWithGoogleNative();
    expect(token2).toBe('second-token');
    expect(mockGoogleSignin.configure).toHaveBeenCalledTimes(1);
  });

  it('returns null when sign-in is cancelled via response type', async () => {
    process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID = 'test-web-client-id';
    mockGoogleSignin.hasPlayServices.mockResolvedValue(true);
    mockGoogleSignin.signIn.mockResolvedValue({ type: 'cancelled', data: null });

    const { signInWithGoogleNative } = loadNativeAuth();
    const result = await signInWithGoogleNative();

    expect(result).toBeNull();
  });

  it('returns null when sign-in rejects with SIGN_IN_CANCELLED', async () => {
    process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID = 'test-web-client-id';
    mockGoogleSignin.hasPlayServices.mockResolvedValue(true);
    const err = Object.assign(new Error('cancelled'), { code: 'SIGN_IN_CANCELLED' });
    mockGoogleSignin.signIn.mockRejectedValue(err);

    const { signInWithGoogleNative } = loadNativeAuth();
    const result = await signInWithGoogleNative();

    expect(result).toBeNull();
  });

  it('throws auth.error.no_token when idToken is missing in success response', async () => {
    process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID = 'test-web-client-id';
    mockGoogleSignin.hasPlayServices.mockResolvedValue(true);
    mockGoogleSignin.signIn.mockResolvedValue({
      type: 'success',
      data: {
        idToken: null,
        user: { id: '1', name: 'Test', email: 'test@test.com', photo: null, familyName: null, givenName: null },
        scopes: [],
        serverAuthCode: null,
      },
    });

    const { signInWithGoogleNative } = loadNativeAuth();
    await expect(signInWithGoogleNative()).rejects.toThrow('auth.error.no_token');
  });

  it('throws auth.error.play_services when hasPlayServices rejects with PLAY_SERVICES_NOT_AVAILABLE', async () => {
    process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID = 'test-web-client-id';
    const err = Object.assign(new Error('missing'), { code: 'PLAY_SERVICES_NOT_AVAILABLE' });
    mockGoogleSignin.hasPlayServices.mockRejectedValue(err);

    const { signInWithGoogleNative } = loadNativeAuth();
    await expect(signInWithGoogleNative()).rejects.toThrow('auth.error.play_services');
  });

  it('throws auth.error.play_services when hasPlayServices returns false', async () => {
    process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID = 'test-web-client-id';
    mockGoogleSignin.hasPlayServices.mockResolvedValue(false);

    const { signInWithGoogleNative } = loadNativeAuth();
    await expect(signInWithGoogleNative()).rejects.toThrow('auth.error.play_services');
  });

  it('throws auth.error.not_configured when env var is missing without calling the lib', async () => {
    const { signInWithGoogleNative } = loadNativeAuth();
    await expect(signInWithGoogleNative()).rejects.toThrow('auth.error.not_configured');
    expect(mockGoogleSignin.configure).not.toHaveBeenCalled();
    expect(mockGoogleSignin.hasPlayServices).not.toHaveBeenCalled();
    expect(mockGoogleSignin.signIn).not.toHaveBeenCalled();
  });

  it('throws auth.error.login_failed on unknown errors', async () => {
    process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID = 'test-web-client-id';
    mockGoogleSignin.hasPlayServices.mockResolvedValue(true);
    mockGoogleSignin.signIn.mockRejectedValue(new Error('unknown'));

    const { signInWithGoogleNative } = loadNativeAuth();
    await expect(signInWithGoogleNative()).rejects.toThrow('auth.error.login_failed');
  });

  it('passes through auth.error.* errors thrown by signIn', async () => {
    process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID = 'test-web-client-id';
    mockGoogleSignin.hasPlayServices.mockResolvedValue(true);
    mockGoogleSignin.signIn.mockRejectedValue(new Error('auth.error.some_custom'));

    const { signInWithGoogleNative } = loadNativeAuth();
    await expect(signInWithGoogleNative()).rejects.toThrow('auth.error.some_custom');
  });

  it('throws play_services when signIn rejects with PLAY_SERVICES_NOT_AVAILABLE', async () => {
    process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID = 'test-web-client-id';
    mockGoogleSignin.hasPlayServices.mockResolvedValue(true);
    const err = Object.assign(new Error('missing'), { code: 'PLAY_SERVICES_NOT_AVAILABLE' });
    mockGoogleSignin.signIn.mockRejectedValue(err);

    const { signInWithGoogleNative } = loadNativeAuth();
    await expect(signInWithGoogleNative()).rejects.toThrow('auth.error.play_services');
  });
});

describe('signOutGoogleNative', () => {
  beforeEach(() => {
    jest.resetModules();
    jest.clearAllMocks();
    delete process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID;
  });

  it('calls GoogleSignin.signOut', async () => {
    process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID = 'test-web-client-id';
    mockGoogleSignin.hasPlayServices.mockResolvedValue(true);
    mockGoogleSignin.signIn.mockResolvedValue({
      type: 'success',
      data: {
        idToken: 'token',
        user: { id: '1', name: 'Test', email: 'test@test.com', photo: null, familyName: null, givenName: null },
        scopes: [],
        serverAuthCode: null,
      },
    });

    const { signInWithGoogleNative, signOutGoogleNative } = loadNativeAuth();
    await signInWithGoogleNative();
    mockGoogleSignin.signOut.mockResolvedValue(null);

    await signOutGoogleNative();
    expect(mockGoogleSignin.signOut).toHaveBeenCalled();
  });

  it('swallows errors from signOut', async () => {
    process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID = 'test-web-client-id';
    mockGoogleSignin.hasPlayServices.mockResolvedValue(true);
    mockGoogleSignin.signIn.mockResolvedValue({
      type: 'success',
      data: {
        idToken: 'token',
        user: { id: '1', name: 'Test', email: 'test@test.com', photo: null, familyName: null, givenName: null },
        scopes: [],
        serverAuthCode: null,
      },
    });

    const { signInWithGoogleNative, signOutGoogleNative } = loadNativeAuth();
    await signInWithGoogleNative();
    mockGoogleSignin.signOut.mockRejectedValue(new Error('boo'));

    await expect(signOutGoogleNative()).resolves.toBeUndefined();
  });

  it('is a no-op when not configured', async () => {
    const { signOutGoogleNative } = loadNativeAuth();
    await signOutGoogleNative();
    expect(mockGoogleSignin.signOut).not.toHaveBeenCalled();
  });
});

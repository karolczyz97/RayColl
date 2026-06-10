export async function signInWithGoogleNative(): Promise<string | null> {
  throw new Error('auth.error.not_configured');
}

export async function signOutGoogleNative(): Promise<void> {
  // no-op on web
}

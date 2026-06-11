import { GoogleSignin, statusCodes, isErrorWithCode } from '@react-native-google-signin/google-signin';
import { AppState } from 'react-native';

let configured = false;

// TEMP diagnostics for the "picker dismisses but nothing happens" bug. Remove once diagnosed.
function authDebug(message: string): void {
  if (__DEV__) {
    console.log(
      `[auth-debug ${new Date().toISOString().slice(11, 23)} app=${AppState.currentState}] ${message}`,
    );
  }
}

export async function signInWithGoogleNative(): Promise<string | null> {
  const webClientId = process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID;
  if (!webClientId) {
    throw new Error('auth.error.not_configured');
  }

  if (!configured) {
    GoogleSignin.configure({ webClientId });
    configured = true;
  }

  try {
    const hasServices = await GoogleSignin.hasPlayServices({
      showPlayServicesUpdateDialog: true,
    });
    if (!hasServices) {
      throw new Error('auth.error.play_services');
    }
  } catch (err: unknown) {
    if (err instanceof Error && err.message.startsWith('auth.error.')) {
      throw err;
    }
    if (isErrorWithCode(err) && err.code === statusCodes.PLAY_SERVICES_NOT_AVAILABLE) {
      throw new Error('auth.error.play_services');
    }
    throw new Error('auth.error.play_services');
  }

  try {
    authDebug('GoogleSignin.signIn() start');
    const response = await GoogleSignin.signIn();
    authDebug(
      `GoogleSignin.signIn() resolved type=${response.type} hasIdToken=${!!response.data?.idToken}`,
    );

    if (response.type === 'cancelled') {
      return null;
    }

    const idToken = response.data?.idToken;
    if (!idToken) {
      throw new Error('auth.error.no_token');
    }

    return idToken;
  } catch (err: unknown) {
    authDebug(`GoogleSignin.signIn() threw: ${err instanceof Error ? err.message : String(err)}`);
    if (err instanceof Error && err.message.startsWith('auth.error.')) {
      throw err;
    }
    if (isErrorWithCode(err)) {
      if (err.code === statusCodes.SIGN_IN_CANCELLED) {
        return null;
      }
      if (err.code === statusCodes.PLAY_SERVICES_NOT_AVAILABLE) {
        throw new Error('auth.error.play_services');
      }
    }
    throw new Error('auth.error.login_failed');
  }
}

export async function signOutGoogleNative(): Promise<void> {
  if (!configured) return;
  try {
    await GoogleSignin.signOut();
  } catch {
    // best-effort: silently swallow
  }
}

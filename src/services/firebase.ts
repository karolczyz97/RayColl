import { Platform } from 'react-native';
import {
  GoogleAuthProvider,
  signInWithPopup,
  signInWithCredential,
  signOut as fbSignOut,
  onAuthStateChanged,
} from 'firebase/auth';
import type { User, Unsubscribe } from 'firebase/auth';
import * as Crypto from 'expo-crypto';
import * as WebBrowser from 'expo-web-browser';
import { makeRedirectUri } from 'expo-auth-session';
import { auth, db } from './firebaseClient';
import type { StoreData } from '@/types/models';
import {
  loadUserData as loadFirestoreUserData,
  saveUserData as saveFirestoreUserData,
} from '@/store/persistence/firestorePersistence';

WebBrowser.maybeCompleteAuthSession();

// Firebase app/auth/db live in `firebaseClient` (leaf module) to avoid a
// require cycle with the persistence layer. Re-exported here to preserve the
// existing public API surface of `services/firebase`.
export { auth, db };

function getGoogleClientId(): string {
  if (Platform.OS === 'web') return '';
  const platformId = Platform.OS === 'ios'
    ? process.env.EXPO_PUBLIC_GOOGLE_IOS_CLIENT_ID
    : process.env.EXPO_PUBLIC_GOOGLE_ANDROID_CLIENT_ID;
  return platformId || process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID || '';
}

export async function signInWithGoogle(): Promise<User | null> {
  if (!auth) {
    throw new Error('auth.error.not_configured');
  }

  if (Platform.OS === 'web') {
    const provider = new GoogleAuthProvider();
    try {
      const result = await signInWithPopup(auth, provider);
      return result.user;
    } catch (err: unknown) {
      if (err instanceof Error && /popup|cancel/i.test(err.message)) {
        return null;
      }
      throw err;
    }
  }

  const clientId = getGoogleClientId();
  if (!clientId) {
    throw new Error('auth.error.not_configured');
  }

  const nonce = Crypto.randomUUID();
  const state = Crypto.randomUUID();

  const redirectUrl = makeRedirectUri({ scheme: 'raycoll' });

  const authUrl =
    `https://accounts.google.com/o/oauth2/v2/auth?` +
    `client_id=${encodeURIComponent(clientId)}` +
    `&redirect_uri=${encodeURIComponent(redirectUrl)}` +
    `&response_type=id_token` +
    `&scope=${encodeURIComponent('openid email profile')}` +
    `&nonce=${nonce}` +
    `&state=${state}`;

  let result;
  try {
    result = await WebBrowser.openAuthSessionAsync(authUrl, redirectUrl);
  } catch {
    throw new Error('auth.error.login_failed');
  }

  if (result.type === 'cancel' || result.type === 'dismiss') {
    return null;
  }

  if (result.type !== 'success' || !result.url) {
    return null;
  }

  const hash = result.url.split('#')[1] || '';
  const params = new URLSearchParams(hash);

  const returnedState = params.get('state');
  if (returnedState !== state) {
    throw new Error('auth.error.state_mismatch');
  }

  const idToken = params.get('id_token');
  if (!idToken) {
    throw new Error('auth.error.no_token');
  }

  try {
    const credential = GoogleAuthProvider.credential(idToken);
    const userCredential = await signInWithCredential(auth, credential);
    return userCredential.user;
  } catch (err: unknown) {
    if (err instanceof Error && /invalid_id_token|auth/i.test(err.message)) {
      throw new Error('auth.error.invalid_token');
    }
    throw err;
  }
}

export async function signOutUser(): Promise<void> {
  if (auth) await fbSignOut(auth);
}

export function onAuthChange(cb: (user: User | null) => void): Unsubscribe {
  if (!auth) {
    cb(null);
    return () => {};
  }
  return onAuthStateChanged(auth, cb);
}

export async function saveUserData(uid: string, data: StoreData): Promise<void> {
  await saveFirestoreUserData(uid, data);
}

export async function loadUserData(uid: string): Promise<StoreData | null> {
  if (!db) return null;
  return loadFirestoreUserData(uid);
}

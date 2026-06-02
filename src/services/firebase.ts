import { Platform } from 'react-native';
import {
  GoogleAuthProvider,
  signInWithPopup,
  signInWithCredential,
  signOut as fbSignOut,
  onAuthStateChanged,
} from 'firebase/auth';
import type { User, Unsubscribe } from 'firebase/auth';
import { doc, getDoc } from 'firebase/firestore';
import * as Crypto from 'expo-crypto';
import * as WebBrowser from 'expo-web-browser';
import { makeRedirectUri } from 'expo-auth-session';
import { auth, db } from './firebaseClient';
import type { StoreData } from '../store/persistence/localPersistence';
import { migrateLegacyUserDataToV2 } from '../store/persistence/firestoreMigration';
import { FIRESTORE_SCHEMA_VERSION } from '../store/persistence/firestoreSchema';
import { loadUserDataV2, saveUserDataV2 } from '../store/persistence/firestoreV2Persistence';
import { validateBackupData } from '../utils/backupValidation';

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

export type UserData = StoreData;

export async function saveUserData(uid: string, data: UserData): Promise<void> {
  await saveUserDataV2(uid, data);
}

export async function loadUserData(uid: string): Promise<UserData | null> {
  if (!db) return null;

  const rootRef = doc(db, 'users', uid);
  const rootSnap = await getDoc(rootRef);

  if (!rootSnap.exists()) {
    return null;
  }

  const rootData = rootSnap.data() as Record<string, unknown>;

  if (rootData.schemaVersion === FIRESTORE_SCHEMA_VERSION) {
    return loadUserDataV2(uid);
  }

  if ('groups' in rootData && 'studyModes' in rootData && 'activityHeatmap' in rootData) {
    const legacyDataCandidate: unknown = {
      groups: rootData.groups,
      studyModes: rootData.studyModes,
      activityHeatmap: rootData.activityHeatmap,
    };

    validateBackupData(legacyDataCandidate);
    const legacyData = legacyDataCandidate as UserData;
    return migrateLegacyUserDataToV2(uid, legacyData);
  }

  return loadUserDataV2(uid);
}

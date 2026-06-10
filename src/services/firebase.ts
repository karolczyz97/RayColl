import { Platform } from 'react-native';
import {
  GoogleAuthProvider,
  signInWithPopup,
  signInWithCredential,
  signOut as fbSignOut,
  onAuthStateChanged,
} from 'firebase/auth';
import type { User, Unsubscribe } from 'firebase/auth';
import { auth, db } from './firebaseClient';
import type { StoreData } from '@/types/models';
import {
  loadUserData as loadFirestoreUserData,
  saveUserData as saveFirestoreUserData,
} from '@/store/persistence/firestorePersistence';
import {
  signInWithGoogleNative,
  signOutGoogleNative,
} from './googleNativeAuth';

// Firebase app/auth/db live in `firebaseClient` (leaf module) to avoid a
// require cycle with the persistence layer. Re-exported here to preserve the
// existing public API surface of `services/firebase`.
export { auth, db };

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

  const idToken = await signInWithGoogleNative();
  if (idToken === null) return null;

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
  await signOutGoogleNative();
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

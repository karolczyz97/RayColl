import { Platform } from 'react-native';
import { initializeApp } from 'firebase/app';
import {
  initializeAuth,
  getAuth,
  GoogleAuthProvider,
  signInWithPopup,
  signInWithCredential,
  signOut as fbSignOut,
  onAuthStateChanged,
  // @ts-ignore
  getReactNativePersistence,
} from 'firebase/auth';
import type { User, Unsubscribe } from 'firebase/auth';
import {
  initializeFirestore,
  persistentLocalCache,
  persistentMultipleTabManager,
  doc,
  setDoc,
  getDoc,
} from 'firebase/firestore';
import AsyncStorage from '@react-native-async-storage/async-storage';
import * as WebBrowser from 'expo-web-browser';
import { makeRedirectUri } from 'expo-auth-session';
import type { FlashcardGroup, StudyMode } from '../types/models';

WebBrowser.maybeCompleteAuthSession();

const firebaseConfig = {
  apiKey: process.env.EXPO_PUBLIC_FIREBASE_API_KEY || '',
  authDomain: process.env.EXPO_PUBLIC_FIREBASE_AUTH_DOMAIN || '',
  projectId: process.env.EXPO_PUBLIC_FIREBASE_PROJECT_ID || '',
  storageBucket: process.env.EXPO_PUBLIC_FIREBASE_STORAGE_BUCKET || '',
  messagingSenderId: process.env.EXPO_PUBLIC_FIREBASE_MESSAGING_SENDER_ID || '',
  appId: process.env.EXPO_PUBLIC_FIREBASE_APP_ID || '',
};

const isConfigured = !!firebaseConfig.apiKey && !!firebaseConfig.projectId;

const app = isConfigured ? initializeApp(firebaseConfig) : null;

export const auth = app
  ? Platform.OS === 'web'
    ? getAuth(app)
    : initializeAuth(app, {
        persistence: getReactNativePersistence(AsyncStorage),
      })
  : null;

export const db = app
  ? initializeFirestore(app, {
      localCache: persistentLocalCache(
        Platform.OS === 'web' ? { tabManager: persistentMultipleTabManager() } : {}
      ),
    })
  : null;

export async function signInWithGoogle(): Promise<User | null> {
  if (!auth) {
    console.warn('Firebase not configured');
    return null;
  }

  if (Platform.OS === 'web') {
    const provider = new GoogleAuthProvider();
    const result = await signInWithPopup(auth, provider);
    return result.user;
  } else {
    const clientId = process.env.EXPO_PUBLIC_GOOGLE_CLIENT_ID || '';
    if (!clientId) {
      console.warn('Google Client ID not configured in .env (EXPO_PUBLIC_GOOGLE_CLIENT_ID)');
    }

    const redirectUrl = makeRedirectUri({
      scheme: 'raycoll',
    });

    const authUrl =
      `https://accounts.google.com/o/oauth2/v2/auth?` +
      `client_id=${clientId}` +
      `&redirect_uri=${encodeURIComponent(redirectUrl)}` +
      `&response_type=id_token` +
      `&scope=${encodeURIComponent('openid email profile')}` +
      `&nonce=raycoll_nonce`;

    try {
      const result = await WebBrowser.openAuthSessionAsync(authUrl, redirectUrl);

      if (result.type === 'success' && result.url) {
        const hash = result.url.split('#')[1] || result.url.split('?')[1] || '';
        const params = new URLSearchParams(hash);
        const idToken = params.get('id_token');

        if (idToken) {
          const credential = GoogleAuthProvider.credential(idToken);
          const userCredential = await signInWithCredential(auth, credential);
          return userCredential.user;
        }
      }
    } catch (err) {
      console.error('Native sign in error:', err);
    }

    return null;
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

export interface UserData {
  groups: FlashcardGroup[];
  studyModes: StudyMode[];
  activityHeatmap: Record<string, number>;
}

export async function saveUserData(uid: string, data: UserData): Promise<void> {
  if (!db) return;
  await setDoc(doc(db, 'users', uid), data as unknown as Record<string, unknown>);
}

export async function loadUserData(uid: string): Promise<UserData | null> {
  if (!db) return null;
  const snap = await getDoc(doc(db, 'users', uid));
  return snap.exists() ? (snap.data() as UserData) : null;
}

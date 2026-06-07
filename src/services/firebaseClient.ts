import { Platform } from 'react-native';
import { initializeApp } from 'firebase/app';
import { initializeAuth, getAuth, getReactNativePersistence } from 'firebase/auth';
import {
  initializeFirestore,
  persistentLocalCache,
  persistentMultipleTabManager,
} from 'firebase/firestore';
import { secureAuthPersistence } from './secureAuthPersistence';

/**
 * Leaf module that owns Firebase initialization (app / auth / db).
 *
 * It must NOT import anything from the store/persistence layer so that
 * `firestorePersistence` and `firebase.ts` can both import `db`/`auth`
 * from here without creating a require cycle.
 */

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
        persistence: getReactNativePersistence(secureAuthPersistence),
      })
  : null;

export const db = app
  ? initializeFirestore(app, {
      localCache: persistentLocalCache(
        Platform.OS === 'web' ? { tabManager: persistentMultipleTabManager() } : {},
      ),
    })
  : null;

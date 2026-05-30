import { router } from 'expo-router';

export function safeBack() {
  if (router.canGoBack()) {
    router.back();
  } else {
    router.replace('/');
  }
}

import { router } from 'expo-router';
import { ROUTES } from '@/constants/routes';

export function safeBack() {
  if (router.canGoBack()) {
    router.back();
  } else {
    router.navigate(ROUTES.HOME);
  }
}

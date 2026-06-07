import { router } from 'expo-router';
import { ROUTES } from '@/constants/routes';

/**
 * Navigate "up" the screen hierarchy. Every secondary screen in the app sits one
 * level below the dashboard, so "up" is always Home, regardless of how the user
 * got there (e.g. Deck settings -> rail Stats -> up should land on Home, not
 * Settings).
 *
 * We pop the whole stack back to its root rather than `navigate('/')`: on native
 * Android `navigate` pushes a *duplicate* dashboard on top instead of returning
 * to the existing one, which buries the real root so the system back gesture from
 * the dashboard appears to "go forward" into Settings/Stats. `dismissAll` returns
 * to the existing root, keeping the back stack correct (and predictive back sane).
 */
export function navigateUp() {
  if (router.canDismiss()) {
    router.dismissAll();
  } else {
    router.replace(ROUTES.HOME);
  }
}

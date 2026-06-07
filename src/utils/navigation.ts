import { router } from 'expo-router';
import { ROUTES } from '@/constants/routes';

/**
 * Navigate "up" the screen hierarchy. Every secondary screen in the app sits one
 * level below the dashboard, so "up" is always Home. Using navigate (not back)
 * makes the top-bar arrow hierarchical: e.g. Deck settings -> (rail) Stats ->
 * back returns to Home, not to Deck settings. The OS/system back gesture is left
 * untouched, so Android predictive back keeps its default reverse-chronological
 * behavior.
 */
export function navigateUp() {
  router.navigate(ROUTES.HOME);
}

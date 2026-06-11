import { useEffect, useRef, useState } from 'react';

/**
 * Debounced visibility for transient progress indicators.
 *
 * - `active` must stay true for `showDelayMs` before anything is shown, so
 *   sub-second background operations never flash on screen.
 * - Once shown, visibility is held for at least `minVisibleMs`, so a banner
 *   that did appear never blinks away after a few frames.
 */
export function useDelayedVisibility(
  active: boolean,
  showDelayMs = 1200,
  minVisibleMs = 1500,
): boolean {
  const [visible, setVisible] = useState(false);
  const shownAtRef = useRef(0);

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout> | null = null;

    if (active && !visible) {
      timer = setTimeout(() => {
        shownAtRef.current = Date.now();
        setVisible(true);
      }, showDelayMs);
    } else if (!active && visible) {
      const elapsed = Date.now() - shownAtRef.current;
      if (elapsed >= minVisibleMs) {
        setVisible(false);
      } else {
        timer = setTimeout(() => setVisible(false), minVisibleMs - elapsed);
      }
    }

    return () => {
      if (timer) clearTimeout(timer);
    };
  }, [active, visible, showDelayMs, minVisibleMs]);

  return visible;
}

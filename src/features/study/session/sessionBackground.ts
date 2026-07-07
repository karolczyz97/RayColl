import type { AtomicStep } from '@/types/models';

/**
 * Mode is capable of running hands-free (no user interaction required per card).
 * V1: excludes modes with manual ratings or microphone-based speech recognition.
 */
export function isModeHandsFreeCapable(steps: AtomicStep[]): boolean {
  for (const step of steps) {
    if (step.type === 'show_ratings' || step.type === 'listen_and_check') {
      return false;
    }
  }
  return steps.length > 0;
}

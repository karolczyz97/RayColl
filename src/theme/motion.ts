export const MOTION = {
  enterDelayStep: 70,
  enterDelayMax: 280,
  fadeDuration: 300,
  spring: {
    damping: 18,
    stiffness: 140,
    mass: 0.8,
  },
};

export function getEnterDelay(order = 0) {
  return Math.min(Math.max(0, order) * MOTION.enterDelayStep, MOTION.enterDelayMax);
}

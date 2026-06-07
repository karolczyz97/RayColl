import { Platform } from 'react-native';
import * as Haptics from 'expo-haptics';

type HapticTask = () => Promise<void>;

function runHaptic(task: HapticTask): void {
  void task().catch(() => {
    // Haptics are best-effort; unsupported devices and browsers should not affect study flow.
  });
}

function androidOrFallback(
  androidType: Haptics.AndroidHaptics,
  fallback: HapticTask,
): HapticTask {
  return Platform.OS === 'android'
    ? () => Haptics.performAndroidHapticsAsync(androidType)
    : fallback;
}

export function playSelectionHaptic(): void {
  runHaptic(
    androidOrFallback(
      Haptics.AndroidHaptics.Segment_Tick,
      () => Haptics.selectionAsync(),
    ),
  );
}

export function playCardPeekHaptic(): void {
  runHaptic(
    androidOrFallback(
      Haptics.AndroidHaptics.Gesture_Start,
      () => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light),
    ),
  );
}

export function playStudyActionHaptic(): void {
  runHaptic(
    androidOrFallback(
      Haptics.AndroidHaptics.Context_Click,
      () => Haptics.impactAsync(Haptics.ImpactFeedbackStyle.Light),
    ),
  );
}

export function playSuccessHaptic(): void {
  runHaptic(
    androidOrFallback(
      Haptics.AndroidHaptics.Confirm,
      () => Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success),
    ),
  );
}

export function playWarningHaptic(): void {
  runHaptic(
    androidOrFallback(
      Haptics.AndroidHaptics.Context_Click,
      () => Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning),
    ),
  );
}

export function playErrorHaptic(): void {
  runHaptic(
    androidOrFallback(
      Haptics.AndroidHaptics.Reject,
      () => Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error),
    ),
  );
}

export function playRatingHaptic(rating: number): void {
  if (rating <= 1) {
    playErrorHaptic();
    return;
  }
  if (rating === 2) {
    playWarningHaptic();
    return;
  }
  if (rating >= 4) {
    playSuccessHaptic();
    return;
  }
  playSelectionHaptic();
}

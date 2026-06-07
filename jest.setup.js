const matchers = require('@testing-library/react-native/matchers');

expect.extend(matchers);

global.__expoRouterMock = {
  pathname: '/',
  router: {
    navigate: jest.fn(),
    push: jest.fn(),
    back: jest.fn(),
    canGoBack: jest.fn(() => true),
  },
};

jest.mock('expo-router', () => ({
  router: global.__expoRouterMock.router,
  usePathname: () => global.__expoRouterMock.pathname,
  useLocalSearchParams: () => ({}),
}));

jest.mock('@react-native-async-storage/async-storage', () =>
  require('@react-native-async-storage/async-storage/jest/async-storage-mock'),
);

jest.mock('react-native-reanimated', () => {
  const React = require('react');
  const { View } = require('react-native');

  const animationBuilder = {
    duration: () => animationBuilder,
    delay: () => animationBuilder,
    damping: () => animationBuilder,
    mass: () => animationBuilder,
    springify: () => animationBuilder,
    stiffness: () => animationBuilder,
  };

  const Animated = {
    View,
    createAnimatedComponent: (Component) => Component,
    useSharedValue: (value) => ({ value }),
    useAnimatedStyle: (callback) => callback(),
    withSpring: (value) => value,
    withTiming: (value) => value,
    withRepeat: (value) => value,
    withSequence: (...values) => values[values.length - 1],
    FadeIn: animationBuilder,
    FadeInUp: animationBuilder,
    FadeInDown: animationBuilder,
    ZoomIn: animationBuilder,
  };

  return {
    __esModule: true,
    default: Animated,
    ...Animated,
  };
});

jest.mock('expo-application', () => ({
  nativeBuildVersion: null,
}));

jest.mock('expo-haptics', () => ({
  AndroidHaptics: {
    Confirm: 'confirm',
    Context_Click: 'context-click',
    Gesture_Start: 'gesture-start',
    Reject: 'reject',
    Segment_Tick: 'segment-tick',
  },
  ImpactFeedbackStyle: {
    Light: 'light',
  },
  NotificationFeedbackType: {
    Error: 'error',
    Success: 'success',
    Warning: 'warning',
  },
  impactAsync: jest.fn(() => Promise.resolve()),
  notificationAsync: jest.fn(() => Promise.resolve()),
  performAndroidHapticsAsync: jest.fn(() => Promise.resolve()),
  selectionAsync: jest.fn(() => Promise.resolve()),
}));

jest.mock('expo-secure-store', () => {
  const store = new Map();

  return {
    AFTER_FIRST_UNLOCK: 'AFTER_FIRST_UNLOCK',
    deleteItemAsync: jest.fn((key) => {
      store.delete(key);
      return Promise.resolve();
    }),
    getItemAsync: jest.fn((key) => Promise.resolve(store.get(key) ?? null)),
    isAvailableAsync: jest.fn(() => Promise.resolve(true)),
    setItemAsync: jest.fn((key, value) => {
      store.set(key, value);
      return Promise.resolve();
    }),
  };
});

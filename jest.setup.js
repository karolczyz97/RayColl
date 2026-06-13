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
  const { View, Text } = require('react-native');

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
    Text,
    createAnimatedComponent: (Component) => Component,
    useSharedValue: (value) => ({ value }),
    useAnimatedStyle: (callback) => callback(),
    withSpring: (value) => value,
    withTiming: (value) => value,
    withRepeat: (value) => value,
    withSequence: (...values) => values[values.length - 1],
    Extrapolation: { CLAMP: 'clamp', EXTEND: 'extend', IDENTITY: 'identity' },
    interpolate: (value, inputRange = [0, 1], outputRange = [0, 1]) => {
      const last = inputRange.length - 1;
      if (value <= inputRange[0]) return outputRange[0];
      if (value >= inputRange[last]) return outputRange[last];
      const t = (value - inputRange[0]) / (inputRange[1] - inputRange[0]);
      return outputRange[0] + t * (outputRange[1] - outputRange[0]);
    },
    interpolateColor: (value, inputRange = [0, 1], outputRange = ['#000', '#000']) =>
      value <= inputRange[0] ? outputRange[0] : outputRange[outputRange.length - 1],
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

jest.mock('@react-native-google-signin/google-signin', () => ({
  GoogleSignin: {
    configure: jest.fn(),
    hasPlayServices: jest.fn(() => Promise.resolve(true)),
    signIn: jest.fn(() => Promise.resolve({ type: 'cancelled', data: null })),
    signOut: jest.fn(() => Promise.resolve(null)),
  },
  statusCodes: {
    SIGN_IN_CANCELLED: 'SIGN_IN_CANCELLED',
    IN_PROGRESS: 'IN_PROGRESS',
    PLAY_SERVICES_NOT_AVAILABLE: 'PLAY_SERVICES_NOT_AVAILABLE',
    SIGN_IN_REQUIRED: 'SIGN_IN_REQUIRED',
    NULL_PRESENTER: 'NULL_PRESENTER',
  },
  isErrorWithCode: (err) => typeof err === 'object' && err !== null && 'code' in err,
  isCancelledResponse: (r) => r?.type === 'cancelled',
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

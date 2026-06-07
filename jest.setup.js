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

import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import { Pressable, Text } from 'react-native';

import {
  NavigationBlockerProvider,
  useNavigationBlocker,
  useNavigationBlockerState,
  type NavigationBlockerRequestLeave,
} from '../NavigationBlockerContext';

function BlockerProbe({
  active,
  requestLeave,
}: {
  active: boolean;
  requestLeave: NavigationBlockerRequestLeave;
}) {
  useNavigationBlocker({
    active,
    requestLeave,
  });

  return null;
}

function NavigationProbe({ navigate }: { navigate: () => void }) {
  const { isNavigationBlocked, requestBlockedNavigation } = useNavigationBlockerState();

  return (
    <>
      <Text testID="blocked">{String(isNavigationBlocked)}</Text>
      <Pressable
        accessibilityRole="button"
        onPress={() => {
          const handled = isNavigationBlocked
            ? requestBlockedNavigation(navigate)
            : false;

          if (!handled) {
            navigate();
          }
        }}
      >
        <Text>Navigate</Text>
      </Pressable>
    </>
  );
}

function TestTree({
  active,
  navigate,
  requestLeave,
}: {
  active: boolean;
  navigate: () => void;
  requestLeave: NavigationBlockerRequestLeave;
}) {
  return (
    <NavigationBlockerProvider>
      <BlockerProbe active={active} requestLeave={requestLeave} />
      <NavigationProbe navigate={navigate} />
    </NavigationBlockerProvider>
  );
}

describe('NavigationBlockerContext', () => {
  it('routes navigation through an active blocker and unregisters when inactive', () => {
    const navigate = jest.fn();
    const requestLeave = jest.fn();
    const { rerender } = render(
      <TestTree active navigate={navigate} requestLeave={requestLeave} />,
    );

    fireEvent.press(screen.getByText('Navigate'));

    expect(requestLeave).toHaveBeenCalledTimes(1);
    expect(navigate).not.toHaveBeenCalled();

    requestLeave.mock.calls[0][0]();
    expect(navigate).toHaveBeenCalledTimes(1);

    rerender(
      <TestTree active={false} navigate={navigate} requestLeave={requestLeave} />,
    );
    fireEvent.press(screen.getByText('Navigate'));

    expect(requestLeave).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledTimes(2);
  });

  it('uses the most recently registered active blocker', () => {
    const navigate = jest.fn();
    const firstRequestLeave = jest.fn();
    const secondRequestLeave = jest.fn();

    render(
      <NavigationBlockerProvider>
        <BlockerProbe active requestLeave={firstRequestLeave} />
        <BlockerProbe active requestLeave={secondRequestLeave} />
        <NavigationProbe navigate={navigate} />
      </NavigationBlockerProvider>,
    );

    fireEvent.press(screen.getByText('Navigate'));

    expect(firstRequestLeave).not.toHaveBeenCalled();
    expect(secondRequestLeave).toHaveBeenCalledTimes(1);
    expect(navigate).not.toHaveBeenCalled();
  });
});

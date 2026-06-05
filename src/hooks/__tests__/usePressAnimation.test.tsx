import React, { useEffect } from 'react';
import { act, render, screen } from '@testing-library/react-native';
import Animated from 'react-native-reanimated';
import { usePressAnimation } from '../usePressAnimation';

type PressAnimationApi = ReturnType<typeof usePressAnimation>;

function Probe({ onApi }: { onApi: (api: PressAnimationApi) => void }) {
  const api = usePressAnimation(0.9);

  useEffect(() => {
    onApi(api);
  }, [api, onApi]);

  return <Animated.View testID="animated-box" style={api.animatedStyle} />;
}

describe('usePressAnimation', () => {
  it('returns animated style and press handlers that can run under the Reanimated test mock', () => {
    const apis: PressAnimationApi[] = [];

    render(<Probe onApi={(nextApi) => { apis.push(nextApi); }} />);
    const api = apis[0];

    expect(screen.getByTestId('animated-box').props.style).toEqual({
      transform: [{ scale: 1 }],
    });
    expect(typeof api.onPressIn).toBe('function');
    expect(typeof api.onPressOut).toBe('function');

    act(() => {
      api.onPressIn();
      api.onPressOut();
    });
  });
});

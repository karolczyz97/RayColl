import React, { useEffect } from 'react';
import { render } from '@testing-library/react-native';
import { Text } from 'react-native';
import type { MutableRefObject } from 'react';
import { useSyncedRef } from '../useSyncedRef';

function Probe({
  value,
  onRef,
}: {
  value: string;
  onRef: (ref: MutableRefObject<string>) => void;
}) {
  const ref = useSyncedRef(value);

  useEffect(() => {
    onRef(ref);
  }, [onRef, ref, value]);

  return <Text testID="value">synced</Text>;
}

describe('useSyncedRef', () => {
  it('keeps a stable ref object and syncs the current value after rerender', () => {
    const seenRefs: MutableRefObject<string>[] = [];
    const onRef = (ref: MutableRefObject<string>) => {
      seenRefs.push(ref);
    };

    const { rerender } = render(<Probe value="first" onRef={onRef} />);
    rerender(<Probe value="second" onRef={onRef} />);

    expect(seenRefs[0]).toBe(seenRefs[1]);
    expect(seenRefs[1].current).toBe('second');
  });
});

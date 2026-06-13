import React from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { render, screen } from '@testing-library/react-native';

import { TOKENS } from '@/theme/tokens';
import { ScreenContent } from '../ScreenContent';

describe('ScreenContent', () => {
  it('keeps vertical padding for regular scrolling screens', () => {
    const { UNSAFE_getByType } = render(
      <ScreenContent>
        <Text>regular content</Text>
      </ScreenContent>,
    );

    expect(screen.getByText('regular content')).toBeOnTheScreen();
    expect(StyleSheet.flatten(UNSAFE_getByType(View).props.style)).toEqual(
      expect.objectContaining({ paddingVertical: TOKENS.spacing.sm }),
    );
  });

  it('removes wrapper padding for screen-managed full-height content', () => {
    const { UNSAFE_getByType } = render(
      <ScreenContent fill>
        <Text>full height content</Text>
      </ScreenContent>,
    );

    expect(screen.getByText('full height content')).toBeOnTheScreen();
    expect(StyleSheet.flatten(UNSAFE_getByType(View).props.style)).toEqual(
      expect.objectContaining({ flex: 1, minHeight: 0, paddingVertical: 0 }),
    );
  });
});

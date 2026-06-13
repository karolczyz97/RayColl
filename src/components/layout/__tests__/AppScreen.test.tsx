import React from 'react';
import { ScrollView, StyleSheet, Text } from 'react-native';
import { renderAsync, screen } from '@testing-library/react-native';

import { TestProviders } from '../../../test/renderWithAppProviders';
import { TOKENS } from '@/theme/tokens';
import { AppScreen } from '../AppScreen';

describe('AppScreen', () => {
  it('keeps the home header and overlay visible when persistent navigation is active', async () => {
    await renderAsync(
      <TestProviders
        shellContext={{
          isCompact: false,
          isMedium: false,
          isExpanded: true,
          showPersistentNavigation: true,
          navWidth: 80,
          contentWidth: 760,
        }}
      >
        <AppScreen
          scroll={false}
          brand={<Text>RayColl header</Text>}
          right={<Text>Header action</Text>}
          overlay={<Text>Home overlay</Text>}
        >
          <Text>Dashboard content</Text>
        </AppScreen>
      </TestProviders>,
    );

    expect(screen.getByText('RayColl header')).toBeOnTheScreen();
    expect(screen.getByText('Header action')).toBeOnTheScreen();
    expect(screen.getByText('Home overlay')).toBeOnTheScreen();
    expect(screen.getByText('Dashboard content')).toBeOnTheScreen();
  });

  it('uses compact shared bottom padding for scroll screens', async () => {
    const { UNSAFE_getByType } = await renderAsync(
      <TestProviders>
        <AppScreen title="Settings">
          <Text>Settings content</Text>
        </AppScreen>
      </TestProviders>,
    );

    expect(screen.getByText('Settings content')).toBeOnTheScreen();
    expect(StyleSheet.flatten(UNSAFE_getByType(ScrollView).props.contentContainerStyle)).toEqual(
      expect.objectContaining({ paddingBottom: TOKENS.spacing.md }),
    );
  });
});

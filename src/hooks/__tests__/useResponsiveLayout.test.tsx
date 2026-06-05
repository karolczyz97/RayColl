import React, { useEffect } from 'react';
import { render } from '@testing-library/react-native';
import { Text, useWindowDimensions } from 'react-native';
import type { ResponsiveLayout } from '../useResponsiveLayout';
import { TOKENS } from '@/theme/tokens';
import { useResponsiveLayout } from '../useResponsiveLayout';

jest.mock('react-native', () => {
  const actual = jest.requireActual('react-native');
  const mockUseWindowDimensions = jest.fn();
  return new Proxy(actual, {
    get(target, prop) {
      if (prop === 'useWindowDimensions') {
        return mockUseWindowDimensions;
      }
      return target[prop];
    },
  });
});

const mockedUseWindowDimensions = useWindowDimensions as jest.MockedFunction<typeof useWindowDimensions>;

function Probe({ onLayout }: { onLayout: (layout: ResponsiveLayout) => void }) {
  const layout = useResponsiveLayout();

  useEffect(() => {
    onLayout(layout);
  }, [layout, onLayout]);

  return <Text testID="layout">{layout.windowSizeClass}</Text>;
}

function renderLayout(width: number, height = 900) {
  let latest: ResponsiveLayout | null = null;
  mockedUseWindowDimensions.mockReturnValue({ width, height, scale: 1, fontScale: 1 });
  render(<Probe onLayout={(layout) => { latest = layout; }} />);
  return latest!;
}

describe('useResponsiveLayout', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns compact layout without reserved navigation rail width', () => {
    const layout = renderLayout(599);

    expect(layout.windowSizeClass).toBe('compact');
    expect(layout.contentWidth).toBe(599);
    expect(layout.showNavigationRail).toBe(false);
    expect(layout.navWidth).toBe(0);
  });

  it('returns medium layout with navigation rail width reserved', () => {
    const layout = renderLayout(600);

    expect(layout.windowSizeClass).toBe('medium');
    expect(layout.contentWidth).toBe(600 - TOKENS.layout.railWidth);
    expect(layout.showNavigationRail).toBe(true);
    expect(layout.navWidth).toBe(TOKENS.layout.railWidth);
  });

  it('uses content width, not window width, for two-column layout', () => {
    const layout = renderLayout(840);

    expect(layout.windowSizeClass).toBe('expanded');
    expect(layout.contentWidth).toBe(840 - TOKENS.layout.expandedRailWidth);
    expect(layout.contentSizeClass).toBe('medium');
    expect(layout.useTwoColumnLayout).toBe(false);
  });
});

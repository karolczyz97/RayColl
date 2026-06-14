import React from 'react';
import {
  Platform,
  ScrollView,
  StyleSheet,
  View,
  type StyleProp,
  type ViewStyle,
} from 'react-native';
import { useTheme } from 'react-native-paper';
import { SafeAreaView } from 'react-native-safe-area-context';
import { TOKENS } from '@/theme/tokens';
import { AppTopBar } from './AppTopBar';
import { ScreenContent } from './ScreenContent';

interface AppScreenProps {
  title?: string;
  onBack?: () => void;
  /** Right-side slot: action icon(s) for sub-pages or home action cluster. */
  right?: React.ReactNode;
  /** Home-mode top bar: left-side branding (logo + name) instead of a title. */
  brand?: React.ReactNode;
  /** Floating overlay (e.g. FAB) pinned over the content, outside the scroll. */
  overlay?: React.ReactNode;
  children: React.ReactNode;
  scroll?: boolean;
  maxWidth?: number;
  edges?: ('top' | 'left' | 'right' | 'bottom')[];
  contentStyle?: StyleProp<ViewStyle>;
  /** When false, AppTopBar is not rendered. Default true. */
  showBar?: boolean;
  /** Rendered between AppTopBar and the scroll/content area. */
  headerExtension?: React.ReactNode;
}

/**
 * App shell:
 *  1. AppTopBar — full viewport width, fixed (does not scroll).
 *  2. Scroll host — full viewport width, so the scrollbar sits at the WINDOW edge.
 *
 * The scroll host always spans the full width; horizontal padding lives on the
 * scroll *content container*, not on the region wrapping the scroller. That keeps
 * the scrollbar at the window edge and stops card shadows from being clipped at
 * an inset scroller boundary — the same pattern Browse already uses.
 *
 * Web + scroll: a full-width ScrollView; its contentContainer centers the content
 * with horizontal padding. No nested scroll container, so the scrollbar stays at
 * the viewport edge.
 *
 * Web + scroll=false: full-width region with no card; the screen renders its own
 * full-width scroller and centers content itself (e.g. Browse's FlatList).
 *
 * Native: ScrollView (scroll) or View (no scroll); the ScrollView is full-width
 * and its content container carries the horizontal padding.
 */
export function AppScreen({
  title,
  onBack,
  right,
  brand,
  overlay,
  children,
  scroll = true,
  maxWidth,
  edges = ['top', 'left', 'right'],
  contentStyle,
  showBar: showBarProp = true,
  headerExtension,
}: AppScreenProps) {
  const theme = useTheme();
  const isWeb = Platform.OS === 'web';
  const showBar = showBarProp;

  // Reserve space at the bottom of the scroll content so a pinned overlay (e.g. a
  // FAB) never covers the last item. Screens without an overlay keep the compact
  // baseline.
  const scrollBottomPadding = overlay ? TOKENS.layout.fabClearance : TOKENS.spacing.md;

  const screenContent = (
    <ScreenContent maxWidth={maxWidth} fill={!scroll} style={contentStyle}>
      {children}
    </ScreenContent>
  );

  let body: React.ReactNode;

  if (isWeb && scroll) {
    // Full-width ScrollView → scrollbar at the window edge. Content centered inside.
    body = (
      <ScrollView
        style={styles.flexFill}
        className="raycoll-stable-scrollbar"
        contentContainerStyle={[styles.webScrollContent, { paddingBottom: scrollBottomPadding }]}
      >
        {screenContent}
      </ScrollView>
    );
  } else if (isWeb && !scroll) {
    // Screen owns its full-width scroller (e.g. Browse FlatList); no card wrapper.
    body = <View style={styles.flexFill}>{screenContent}</View>;
  } else if (scroll) {
    // Native scroll. Full-width scroller; padding lives on the content container.
    body = (
      <ScrollView
        style={styles.flexFill}
        contentContainerStyle={[styles.nativeScrollContent, { paddingBottom: scrollBottomPadding }]}
      >
        {screenContent}
      </ScrollView>
    );
  } else {
    // Native, screen-managed scroll.
    body = <View style={styles.flexFill}>{screenContent}</View>;
  }

  return (
    <SafeAreaView edges={edges} style={[styles.root, { backgroundColor: theme.colors.background }]}>
      {showBar ? <AppTopBar title={title} onBack={onBack} right={right} brand={brand} /> : null}
      {headerExtension}

      <View style={[styles.contentRegion, { backgroundColor: theme.colors.background }]}>
        {body}
        {overlay}
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
  },
  contentRegion: {
    flex: 1,
    minHeight: 0,
  },
  // Web scroll content: centers the content horizontally, with top breathing room.
  // Bottom padding is applied dynamically (overlay-aware).
  webScrollContent: {
    alignItems: 'center',
    paddingHorizontal: TOKENS.spacing.lg,
    paddingTop: TOKENS.spacing.md,
  },
  // Native scroll content: horizontal padding lives here (not on the wrapping
  // region) so the scroller spans full width and the scrollbar sits at the edge.
  // Bottom padding is applied dynamically (overlay-aware).
  nativeScrollContent: {
    paddingHorizontal: TOKENS.spacing.lg,
  },
  flexFill: {
    flex: 1,
    minHeight: 0,
  },
});

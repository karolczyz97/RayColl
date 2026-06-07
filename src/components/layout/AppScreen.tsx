import React from 'react';
import { Platform, ScrollView, StyleSheet, View, type StyleProp, type ViewStyle } from 'react-native';
import { useTheme } from 'react-native-paper';
import { SafeAreaView } from 'react-native-safe-area-context';
import { TOKENS } from '@/theme/tokens';
import { getElevationStyle } from '@/theme/elevation';
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
 * Web + scroll: a full-width ScrollView; its contentContainer centers a webCard
 * (max 1200px, surface, rounded, shadow). The card is plain scrolling content —
 * no nested scroll container, so the scrollbar stays at the viewport edge.
 *
 * Web + scroll=false: full-width region with no card; the screen renders its own
 * full-width scroller and centers content itself (e.g. Browse's FlatList).
 *
 * Native: ScrollView (scroll) or View (no scroll) with horizontal padding.
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

  const screenContent = (
    <ScreenContent maxWidth={maxWidth} fill={!scroll} style={contentStyle}>
      {children}
    </ScreenContent>
  );

  let body: React.ReactNode;

  if (isWeb && scroll) {
    // Full-width ScrollView → scrollbar at the window edge. Card centered inside.
    body = (
      <ScrollView
        style={styles.flexFill}
        className="raycoll-stable-scrollbar"
        contentContainerStyle={styles.webScrollContent}
      >
        <View
          style={[
            styles.webCard,
            { backgroundColor: theme.colors.surface },
            getElevationStyle(TOKENS.elevation.level3, theme.colors.shadow, Platform.OS),
          ]}
        >
          {screenContent}
        </View>
      </ScrollView>
    );
  } else if (isWeb && !scroll) {
    // Screen owns its full-width scroller (e.g. Browse FlatList); no card wrapper.
    body = <View style={styles.flexFill}>{screenContent}</View>;
  } else if (scroll) {
    // Native scroll.
    body = (
      <ScrollView style={styles.flexFill} contentContainerStyle={styles.nativeScrollContent}>
        {screenContent}
      </ScrollView>
    );
  } else {
    // Native, screen-managed scroll.
    body = <View style={styles.flexFill}>{screenContent}</View>;
  }

  return (
    <SafeAreaView
      edges={edges}
      style={[styles.root, { backgroundColor: theme.colors.background }]}
    >
      {showBar ? (
        <AppTopBar
          title={title}
          onBack={onBack}
          right={right}
          brand={brand}
        />
      ) : null}
      {headerExtension}

      <View
        style={[
          styles.contentRegion,
          { backgroundColor: theme.colors.background },
          // scroll=false screens (Browse/Import/Study) own their horizontal
          // padding via their list/content styles. Applying nativePadding there too
          // double-padded them on native, making them narrower than scroll screens.
          !isWeb && scroll && styles.nativePadding,
        ]}
      >
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
  nativePadding: {
    paddingHorizontal: TOKENS.spacing.lg,
  },
  // Web scroll content: centers the card horizontally, with vertical breathing room.
  webScrollContent: {
    alignItems: 'center',
    paddingTop: TOKENS.spacing.md,
    paddingBottom: TOKENS.spacing.xxl * 2,
  },
  // Web card: centered visual container (max 1200px, surface, rounded, shadow).
  webCard: {
    width: '100%',
    maxWidth: TOKENS.layout.maxWidth,
    paddingHorizontal: TOKENS.spacing.lg,
    borderRadius: TOKENS.radius.xl,
  },
  nativeScrollContent: {
    paddingBottom: TOKENS.spacing.xxl * 2,
  },
  flexFill: {
    flex: 1,
    minHeight: 0,
  },
});

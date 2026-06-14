import React, { useMemo, useState } from 'react';
import { Platform, Pressable, StyleSheet, View } from 'react-native';
import type { StyleProp, ViewStyle } from 'react-native';
import { Card, useTheme } from 'react-native-paper';
import type { ComponentProps } from 'react';
import { TOKENS } from '@/theme/tokens';
import { getContainedSurface } from '@/theme/semanticColors';
import { hexToRgba } from '@/theme/colorUtils';

type PaperCardProps = ComponentProps<typeof Card>;

// All cards share one "contained" treatment (MD3 filled card): a tonal fill, no
// resting shadow, no outline. Interaction feedback follows MD3 — only an
// *interactive* card (one given onPress/onLongPress) shows a subtle hover state
// layer on web; static cards stay flat. No scale, no drop shadow (that was the
// non-MD3 hover that lifted every card, including non-clickable ones).
function AppCardBase({ style, mode: _ignoredMode, elevation: _ignoredElevation, ...props }: PaperCardProps) {
  const theme = useTheme();
  const [hovered, setHovered] = useState(false);

  const interactive = props.onPress != null || props.onLongPress != null;
  const isWeb = Platform.OS === 'web';

  const flatStyle = useMemo(() => (StyleSheet.flatten(style) ?? {}) as ViewStyle, [style]);

  // Match the state-layer overlay to the card's actual corner radius.
  const radius = (flatStyle.borderRadius as number | undefined) ?? TOKENS.radius.xl;

  const layoutStyle = useMemo<ViewStyle>(() => {
    const nextStyle: ViewStyle = {
      alignSelf: flatStyle.alignSelf,
      flex: flatStyle.flex,
      flexBasis: flatStyle.flexBasis,
      flexGrow: flatStyle.flexGrow,
      flexShrink: flatStyle.flexShrink,
      margin: flatStyle.margin,
      marginBottom: flatStyle.marginBottom,
      marginEnd: flatStyle.marginEnd,
      marginHorizontal: flatStyle.marginHorizontal,
      marginLeft: flatStyle.marginLeft,
      marginRight: flatStyle.marginRight,
      marginStart: flatStyle.marginStart,
      marginTop: flatStyle.marginTop,
      marginVertical: flatStyle.marginVertical,
      maxWidth: flatStyle.maxWidth,
      minWidth: flatStyle.minWidth,
      width: flatStyle.width ?? '100%',
    };

    if (isWeb) {
      nextStyle.cursor = interactive ? 'pointer' : 'auto';
    }

    return nextStyle;
  }, [flatStyle, interactive, isWeb]);

  // Propagate flex to the inner layer so flex: 1 works on AppCard.
  const innerFlexStyle = useMemo<StyleProp<ViewStyle>>(
    () => (flatStyle.flex != null ? { flex: flatStyle.flex } : undefined),
    [flatStyle.flex],
  );

  // Filled-card surface. Sits UNDER the caller's style so a screen can still
  // override the radius (or, for `danger`, the border color) when it needs to.
  const surfaceStyle = useMemo<ViewStyle>(
    () => ({ backgroundColor: getContainedSurface(theme), borderRadius: TOKENS.radius.xl }),
    [theme],
  );

  const cardStyle = useMemo(() => [surfaceStyle, style, styles.card], [style, surfaceStyle]);

  const showStateLayer = interactive && isWeb && hovered;

  return (
    <Pressable
      onHoverIn={interactive && isWeb ? () => setHovered(true) : undefined}
      onHoverOut={interactive && isWeb ? () => setHovered(false) : undefined}
      style={layoutStyle}
    >
      <View style={[styles.layer, innerFlexStyle]}>
        <Card {...props} mode="contained" style={cardStyle} />
        {showStateLayer ? (
          <View
            pointerEvents="none"
            style={[
              styles.stateLayer,
              { borderRadius: radius, backgroundColor: hexToRgba(theme.colors.onSurface, 0.05) },
            ]}
          />
        ) : null}
      </View>
    </Pressable>
  );
}

export const AppCard = Object.assign(AppCardBase, {
  Actions: Card.Actions,
  Content: Card.Content,
});

const styles = StyleSheet.create({
  card: {
    width: '100%',
  },
  layer: {
    overflow: 'visible',
  },
  stateLayer: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
  },
});

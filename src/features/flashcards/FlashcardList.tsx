import React from 'react';
import { StyleSheet, View } from 'react-native';
import type { StyleProp, ViewStyle } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
import Animated, { LinearTransition } from 'react-native-reanimated';
import type { Flashcard, FlashcardGroup } from '@/types/models';
import { FlashcardListItem } from '@/components/browse/FlashcardListItem';
import { AppIcon } from '@/components/AppIcon';
import { AnimatedSection } from '@/components/layout/AnimatedSection';
import { getVisiblePageNames } from '@/store/selectors/pages';
import { useNavigationShell } from '@/contexts/NavigationShellContext';
import { TOKENS } from '@/theme/tokens';
import { isExpandedWindowSize } from '@/utils/windowSizeClass';

interface FlashcardListProps {
  cards: Flashcard[];
  group: FlashcardGroup;
  onStartEdit: (card: Flashcard) => void;
  onDelete: (cardId: string) => void;
  style?: StyleProp<ViewStyle>;
  scrollEnabled?: boolean;
  contentContainerStyle?: StyleProp<ViewStyle>;
  keyboardShouldPersistTaps?: 'always' | 'never' | 'handled';
  showHeader?: boolean;
  emptyLabel?: string;
  listHeaderContent?: React.ReactNode;
  itemAnimationOffset?: number;
  className?: string;
  readOnly?: boolean;
  viewHidden?: boolean;
}

export function FlashcardList({
  cards,
  group,
  onStartEdit,
  onDelete,
  style,
  scrollEnabled = true,
  contentContainerStyle,
  keyboardShouldPersistTaps = 'handled',
  showHeader = true,
  emptyLabel,
  listHeaderContent,
  itemAnimationOffset = 0,
  className,
  readOnly,
  viewHidden,
}: FlashcardListProps) {
  const theme = useTheme();
  const { contentWidth } = useNavigationShell();
  const useTwoColumnLayout = isExpandedWindowSize(contentWidth);
  const fillerCount = useTwoColumnLayout && cards.length % 2 === 1 ? 1 : 0;

  const pageNames = viewHidden ? group.pageNames : getVisiblePageNames(group);

  const pageNamesHeader =
    showHeader && cards.length > 0 ? (
      <AnimatedSection order={itemAnimationOffset}>
        <View style={styles.headerRow}>
          {pageNames.filter(Boolean).map((name, i) => (
            <React.Fragment key={i}>
              {i > 0 && (
                <View style={styles.separatorContainer}>
                  <AppIcon
                    name="chevron-right"
                    size={TOKENS.iconSize.xs}
                    color={theme.colors.outline}
                    style={styles.separatorIcon}
                  />
                </View>
              )}
              <View
                style={[
                  styles.pageNameChip,
                  {
                    backgroundColor: theme.colors.surfaceVariant,
                    borderColor: theme.colors.outlineVariant,
                  },
                ]}
              >
                <Text
                  variant="labelSmall"
                  style={[styles.pageNameText, { color: theme.colors.onSurfaceVariant }]}
                >
                  {name}
                </Text>
              </View>
            </React.Fragment>
          ))}
        </View>
      </AnimatedSection>
    ) : null;

  // The page-names header animates at itemAnimationOffset, so the list items
  // start one step later.
  const itemOrderOffset = itemAnimationOffset + 1;

  const listHeader =
    listHeaderContent || pageNamesHeader ? (
      <View style={styles.listHeaderWrapper}>
        {listHeaderContent}
        {pageNamesHeader}
      </View>
    ) : null;

  const content = (
    <>
      {listHeader}
      {cards.length > 0 ? (
        <View style={styles.grid}>
          {cards.map((item, index) => (
            <Animated.View
              key={item.id}
              layout={LinearTransition.duration(TOKENS.motion.duration.medium)}
              style={useTwoColumnLayout ? styles.gridCardCell : styles.listCardCell}
            >
              <AnimatedSection
                order={itemOrderOffset + index}
                style={useTwoColumnLayout && styles.cardStretch}
              >
                 <FlashcardListItem
                  card={item}
                  group={group}
                  onStartEdit={() => onStartEdit(item)}
                  onDelete={() => onDelete(item.id)}
                  readOnly={readOnly}
                  fill={useTwoColumnLayout}
                  viewHidden={viewHidden}
                />
              </AnimatedSection>
            </Animated.View>
          ))}
          {Array.from({ length: fillerCount }).map((_, index) => (
            <View
              key={`filler-${index}`}
              pointerEvents="none"
              style={[styles.gridCardCell, styles.fillerItem]}
            />
          ))}
        </View>
      ) : emptyLabel ? (
        <View style={styles.emptyState}>
          <Text style={[styles.emptyText, { color: theme.colors.onSurfaceVariant }]}>
            {emptyLabel}
          </Text>
        </View>
      ) : null}
    </>
  );

  if (!scrollEnabled) {
    return (
      <Animated.View
        className={className}
        style={[style, styles.listContainer, contentContainerStyle]}
      >
        {content}
      </Animated.View>
    );
  }

  return (
    <Animated.ScrollView
      className={className}
      style={style}
      contentContainerStyle={[styles.listContainer, contentContainerStyle]}
      keyboardShouldPersistTaps={keyboardShouldPersistTaps}
    >
      {content}
    </Animated.ScrollView>
  );
}

const styles = StyleSheet.create({
  listHeaderWrapper: {
    gap: TOKENS.spacing.lg,
    width: '100%',
  },
  listContainer: { gap: TOKENS.spacing.lg },
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    alignItems: 'stretch',
    gap: TOKENS.spacing.lg,
    width: '100%',
  },
  headerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    flexWrap: 'wrap',
    paddingHorizontal: TOKENS.spacing.sm,
    gap: TOKENS.spacing.xs,
  },
  separatorContainer: {
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: TOKENS.spacing.xxs,
  },
  separatorIcon: {
    opacity: 0.5,
  },
  pageNameChip: {
    borderWidth: 1,
    borderRadius: TOKENS.radius.pill,
    paddingVertical: TOKENS.spacing.xs,
    paddingHorizontal: TOKENS.spacing.sm,
  },
  pageNameText: {
    fontWeight: TOKENS.typography.weight.medium,
  },
  listCardCell: {
    width: '100%',
  },
  gridCardCell: {
    alignSelf: 'stretch',
    flexBasis: '47%',
    flexGrow: 1,
    minWidth: 0,
  },
  cardStretch: {
    flex: 1,
  },
  fillerItem: {
    opacity: 0,
  },
  emptyState: {
    alignItems: 'center',
    paddingVertical: TOKENS.spacing.xxl,
    width: '100%',
  },
  emptyText: {
    textAlign: 'center',
  },
});

import React, { useEffect, useRef, useState } from 'react';
import {
  Pressable,
  StyleSheet,
  View,
  type StyleProp,
  type TextStyle,
  type ViewStyle,
} from 'react-native';
import { useTheme } from 'react-native-paper';
import Animated, {
  Easing,
  Extrapolation,
  interpolate,
  interpolateColor,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from 'react-native-reanimated';

import { AppIcon } from '@/components/AppIcon';
import { TOKENS } from '@/theme/tokens';

export interface ExpressiveButtonGroupOption<T extends string = string> {
  value: T;
  label: string;
  /** MaterialCommunityIcons name shown before the label. */
  icon?: string;
  accessibilityLabel?: string;
  /** Override the selected background color for this button (e.g. SRS category tint). */
  selectedBg?: string;
  /** Override the selected content (text/icon) color for this button. */
  selectedContent?: string;
  /** Override the unselected background color for this button. */
  unselectedBg?: string;
  /** Override the unselected content (text/icon) color for this button. */
  unselectedContent?: string;
  disabled?: boolean;
}

// --- Props: single-select vs multi-select ---

interface SingleSelectProps<T extends string> {
  value: T;
  onValueChange: (value: T) => void;
  /** @default false */
  multiSelect?: false;
}

interface MultiSelectProps<T extends string> {
  value: readonly T[];
  /** Called with the **pressed** value; the parent controls the toggle logic. */
  onValueChange: (pressedValue: T) => void;
  multiSelect: true;
}

type SelectionProps<T extends string> = SingleSelectProps<T> | MultiSelectProps<T>;

type ExpressiveButtonGroupProps<T extends string = string> = SelectionProps<T> & {
  buttons: readonly ExpressiveButtonGroupOption<T>[];
  height?: number;
  style?: StyleProp<ViewStyle>;
  accessibilityLabel?: string;
};

const SELECT_SPRING = TOKENS.motion.spring.expressive;
const PRESS_SPRING = TOKENS.motion.spring.tap;
const PRESSED_SCALE = 0.95;
// Outer corners of the whole group read as a connected pill; inner corners
// between segments stay tight. A selected segment morphs all four to a full pill.
const OUTER_RADIUS = TOKENS.radius.lg;
const INNER_RADIUS = TOKENS.radius.sm;
const ICON_SIZE = TOKENS.iconSize.sm;

interface CornerRadii {
  tl: number;
  tr: number;
  bl: number;
  br: number;
}

function getBaseRadii(isFirst: boolean, isLast: boolean): CornerRadii {
  return {
    tl: isFirst ? OUTER_RADIUS : INNER_RADIUS,
    bl: isFirst ? OUTER_RADIUS : INNER_RADIUS,
    tr: isLast ? OUTER_RADIUS : INNER_RADIUS,
    br: isLast ? OUTER_RADIUS : INNER_RADIUS,
  };
}

interface SegmentProps {
  option: ExpressiveButtonGroupOption;
  selected: boolean;
  isFirst: boolean;
  isLast: boolean;
  height: number;
  selectedBg: string;
  unselectedBg: string;
  selectedContent: string;
  unselectedContent: string;
  labelStyle: StyleProp<TextStyle>;
  onPress: () => void;
  /** 'radio' for single-select, 'checkbox' for multi-select */
  accessibilityRoleOverride: 'button' | 'checkbox';
  resetTrigger?: number;
}

function ExpressiveSegment({
  option,
  selected,
  isFirst,
  isLast,
  height,
  selectedBg,
  unselectedBg,
  selectedContent,
  unselectedContent,
  labelStyle,
  onPress,
  accessibilityRoleOverride,
  resetTrigger,
}: SegmentProps) {
  const progress = useSharedValue(selected ? 1 : 0);
  const scale = useSharedValue(1);
  const base = getBaseRadii(isFirst, isLast);
  const pill = height / 2;
  const lastResetTrigger = useRef(resetTrigger);

  useEffect(() => {
    const isReset =
      resetTrigger !== undefined &&
      resetTrigger !== lastResetTrigger.current &&
      resetTrigger > 0;
    lastResetTrigger.current = resetTrigger;

    if (isReset && !option.disabled) {
      // Bounce flash animation: morph to pill, color bounce, then timing back.
      // Total duration = 300ms (150ms to reach peak 1.05, 150ms to return to 0).
      progress.value = withTiming(
        1.05,
        { duration: 150, easing: Easing.out(Easing.quad) },
        (finished) => {
          if (finished) {
            progress.value = withTiming(0, {
              duration: 150,
              easing: Easing.inOut(Easing.quad),
            });
          }
        },
      );
    } else {
      progress.value = withSpring(selected ? 1 : 0, SELECT_SPRING);
    }
  }, [selected, resetTrigger, progress, option.disabled]);

  const animatedStyle = useAnimatedStyle(() => {
    const p = progress.value;
    const clamped = Math.min(1, Math.max(0, p));
    return {
      borderTopLeftRadius: interpolate(clamped, [0, 1], [base.tl, pill], Extrapolation.CLAMP),
      borderTopRightRadius: interpolate(clamped, [0, 1], [base.tr, pill], Extrapolation.CLAMP),
      borderBottomLeftRadius: interpolate(clamped, [0, 1], [base.bl, pill], Extrapolation.CLAMP),
      borderBottomRightRadius: interpolate(clamped, [0, 1], [base.br, pill], Extrapolation.CLAMP),
      backgroundColor: interpolateColor(clamped, [0, 1], [unselectedBg, selectedBg]),
      transform: [{ scale: scale.value }],
    };
  });

  const animatedTextStyle = useAnimatedStyle(() => {
    const p = progress.value;
    const clamped = Math.min(1, Math.max(0, p));
    return {
      color: interpolateColor(clamped, [0, 1], [unselectedContent, selectedContent]),
    };
  });

  const contentColor = selected ? selectedContent : unselectedContent;

  return (
    <Pressable
      testID={`expressive-button-${option.value}`}
      onPress={onPress}
      onPressIn={() => {
        if (!option.disabled) {
          scale.value = withSpring(PRESSED_SCALE, PRESS_SPRING);
        }
      }}
      onPressOut={() => {
        if (!option.disabled) {
          scale.value = withSpring(1, PRESS_SPRING);
        }
      }}
      disabled={option.disabled}
      accessibilityRole={accessibilityRoleOverride}
      accessibilityState={
        accessibilityRoleOverride === 'checkbox'
          ? { checked: selected, disabled: option.disabled }
          : { selected, disabled: option.disabled }
      }
      accessibilityLabel={option.accessibilityLabel ?? option.label}
      style={[styles.pressable, option.disabled && { opacity: 0.35 }]}
    >
      <Animated.View style={[styles.segment, { height }, animatedStyle]}>
        {option.icon ? (
          <AppIcon name={option.icon} size={ICON_SIZE} color={contentColor} />
        ) : null}
        <Animated.Text numberOfLines={1} style={[labelStyle, styles.label, animatedTextStyle]}>
          {option.label}
        </Animated.Text>
      </Animated.View>
    </Pressable>
  );
}

/**
 * Material 3 "connected button group": a row of segments with a small gap, where
 * selected segments morph into high-contrast full pills.
 *
 * Supports both **single-select** (default, `value: string`) and **multi-select**
 * (`multiSelect` + `value: string[]`) per the M3 spec.
 *
 * Each button may override `selectedBg` / `selectedContent` for per-segment
 * theming (e.g. SRS category colors).
 */
export function ExpressiveButtonGroup<T extends string = string>(props: ExpressiveButtonGroupProps<T>) {
  const {
    buttons,
    height = TOKENS.control.height,
    style,
    accessibilityLabel,
  } = props;
  const theme = useTheme();
  const isMulti = props.multiSelect === true;

  const [resetTrigger, setResetTrigger] = useState(0);
  const prevValueRef = useRef(props.value);
  const lastClickedValueRef = useRef<T | null>(null);

  useEffect(() => {
    const prev = prevValueRef.current;
    const current = props.value;
    const lastClicked = lastClickedValueRef.current;

    if (
      isMulti &&
      Array.isArray(current) &&
      current.length === 0 &&
      Array.isArray(prev) &&
      prev.length > 0 &&
      lastClicked !== null
    ) {
      const wasDeselected = prev.includes(lastClicked);
      if (!wasDeselected) {
        setResetTrigger((t) => t + 1);
      }
    }
    prevValueRef.current = current;
  }, [props.value, isMulti]);

  const isSelected = (optionValue: T): boolean => {
    if (isMulti) {
      return (props.value as readonly T[]).includes(optionValue);
    }
    return props.value === optionValue;
  };

  const handlePress = (optionValue: T) => {
    lastClickedValueRef.current = optionValue;
    // Both single and multi-select: emit the pressed value.
    // In single-select the parent sets the new value; in multi-select the
    // parent runs its own toggle logic (e.g. "reset when all selected").
    (props.onValueChange as (v: string) => void)(optionValue);
  };

  const defaultSelectedBg = theme.colors.primary;
  const defaultSelectedContent = theme.colors.onPrimary;

  return (
    <View
      style={[styles.row, style]}
      accessibilityRole={isMulti ? undefined : 'radiogroup'}
      accessibilityLabel={accessibilityLabel}
    >
      {buttons.map((option, index) => (
        <ExpressiveSegment
          key={option.value}
          option={option}
          selected={isSelected(option.value)}
          isFirst={index === 0}
          isLast={index === buttons.length - 1}
          height={height}
          selectedBg={option.selectedBg ?? defaultSelectedBg}
          unselectedBg={option.unselectedBg ?? theme.colors.secondaryContainer}
          selectedContent={option.selectedContent ?? defaultSelectedContent}
          unselectedContent={option.unselectedContent ?? theme.colors.onSecondaryContainer}
          labelStyle={theme.fonts.labelLarge}
          onPress={() => handlePress(option.value)}
          accessibilityRoleOverride={isMulti ? 'checkbox' : 'button'}
          resetTrigger={resetTrigger}
        />
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    width: '100%',
    gap: TOKENS.spacing.xxs,
  },
  pressable: {
    flex: 1,
  },
  segment: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: TOKENS.spacing.xs,
    paddingHorizontal: TOKENS.spacing.sm,
    overflow: 'hidden',
  },
  label: {
    flexShrink: 1,
  },
});

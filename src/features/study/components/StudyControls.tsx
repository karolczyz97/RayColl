import React, { useEffect } from 'react';
import { StyleSheet, View } from 'react-native';
import { Button, Text, useTheme } from 'react-native-paper';
import Animated, {
  FadeIn,
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withSequence,
  withSpring,
} from 'react-native-reanimated';
import { AppIcon } from '../../../components/AppIcon';
import {
  getDangerBgColor,
  getDangerColor,
  getInfoColor,
  getSuccessColor,
  getWarningBgColor,
  getWarningColor,
} from '../../../theme/semanticColors';
import { TOKENS } from '../../../theme/tokens';

interface StudyControlsProps {
  isNarrow: boolean;
  hasTts: boolean;
  hasStt: boolean;
  isTtsPlaying: boolean;
  isSttListening: boolean;
  showRatingButtons: boolean;
  sttResultText: string;
  sttMatchPercent: number;
  getButtonText: (key: string) => string;
  ratingLabels: [string, string, string, string];
  recognizedLabel: string;
  matchPercentLabel: string;
  onRate: (rating: number) => void;
}

export function StudyControls({
  isNarrow,
  hasTts,
  hasStt,
  isTtsPlaying,
  isSttListening,
  showRatingButtons,
  sttResultText,
  sttMatchPercent,
  getButtonText,
  ratingLabels,
  recognizedLabel,
  matchPercentLabel,
  onRate,
}: StudyControlsProps) {
  const theme = useTheme();
  const ttsScale = useSharedValue(1);
  const sttScale = useSharedValue(1);

  useEffect(() => {
    if (isTtsPlaying) {
      ttsScale.value = withRepeat(
        withSequence(withSpring(1.25, { damping: 3 }), withSpring(1, { damping: 3 })),
        -1,
        true,
      );
    } else {
      ttsScale.value = withSpring(1);
    }
  }, [isTtsPlaying, ttsScale]);

  useEffect(() => {
    if (isSttListening) {
      sttScale.value = withRepeat(
        withSequence(withSpring(1.25, { damping: 3 }), withSpring(1, { damping: 3 })),
        -1,
        true,
      );
    } else {
      sttScale.value = withSpring(1);
    }
  }, [isSttListening, sttScale]);

  const ttsIconStyle = useAnimatedStyle(() => ({
    transform: [{ scale: ttsScale.value }],
  }));
  const sttIconStyle = useAnimatedStyle(() => ({
    transform: [{ scale: sttScale.value }],
  }));

  const matchColor =
    sttMatchPercent >= 85
      ? getSuccessColor(theme)
      : sttMatchPercent >= 60
        ? getInfoColor(theme)
        : sttMatchPercent >= 40
          ? getWarningColor(theme)
          : getDangerColor(theme);

  return (
    <View style={[styles.bottomControlZone, { height: hasTts || hasStt ? 180 : 80 }]}>
      {showRatingButtons ? (
        <Animated.View entering={FadeIn.springify()} style={styles.ratingButtonsRow}>
          <Button
            mode="contained-tonal"
            textColor={getDangerColor(theme)}
            buttonColor={getDangerBgColor(theme)}
            style={styles.rateButton}
            compact={isNarrow}
            contentStyle={isNarrow ? styles.compactContent : undefined}
            icon={({ size, color }) => <AppIcon name="replay" size={size} color={color} />}
            onPress={() => onRate(1)}
            accessibilityLabel={ratingLabels[0]}
          >
            {getButtonText('study.rating.1')}
          </Button>
          <Button
            mode="contained-tonal"
            textColor={getWarningColor(theme)}
            buttonColor={getWarningBgColor(theme)}
            style={styles.rateButton}
            compact={isNarrow}
            contentStyle={isNarrow ? styles.compactContent : undefined}
            icon={({ size, color }) => (
              <AppIcon name="emoticon-sad-outline" size={size} color={color} />
            )}
            onPress={() => onRate(2)}
            accessibilityLabel={ratingLabels[1]}
          >
            {getButtonText('study.rating.2')}
          </Button>
          <Button
            mode="contained"
            style={styles.rateButton}
            compact={isNarrow}
            contentStyle={isNarrow ? styles.compactContent : undefined}
            icon={({ size, color }) => (
              <AppIcon name="emoticon-happy-outline" size={size} color={color} />
            )}
            onPress={() => onRate(3)}
            accessibilityLabel={ratingLabels[2]}
          >
            {getButtonText('study.rating.3')}
          </Button>
          <Button
            mode="contained"
            buttonColor={getSuccessColor(theme)}
            textColor={theme.colors.onTertiary}
            style={styles.rateButton}
            compact={isNarrow}
            contentStyle={isNarrow ? styles.compactContent : undefined}
            icon={({ size, color }) => (
              <AppIcon name="emoticon-excited-outline" size={size} color={color} />
            )}
            onPress={() => onRate(4)}
            accessibilityLabel={ratingLabels[3]}
          >
            {getButtonText('study.rating.4')}
          </Button>
        </Animated.View>
      ) : hasTts || hasStt ? (
        <View style={styles.feedbackContainer}>
          <View style={styles.sttTextWrapper}>
            {sttResultText ? (
              <View style={[styles.sttResultBox, { backgroundColor: theme.colors.surfaceVariant }]}>
                <Text variant="labelSmall" style={{ color: theme.colors.onSurfaceVariant }}>
                  {recognizedLabel}
                </Text>
                <Text variant="bodyLarge" style={styles.recognizedText}>
                  {sttResultText}
                </Text>
                {sttMatchPercent > 0 ? (
                  <Text variant="labelSmall" style={[styles.matchPercentText, { color: matchColor }]}>
                    {matchPercentLabel}
                  </Text>
                ) : null}
              </View>
            ) : null}
          </View>

          <View style={styles.audioIconsRow}>
            {hasTts ? (
              <Animated.View style={[styles.iconWrapper, ttsIconStyle]}>
                <AppIcon
                  name="volume-high"
                  size={36}
                  color={isTtsPlaying ? theme.colors.primary : theme.colors.outline}
                />
              </Animated.View>
            ) : null}
            {hasStt ? (
              <Animated.View style={[styles.iconWrapper, sttIconStyle]}>
                <AppIcon
                  name="microphone"
                  size={36}
                  color={isSttListening ? getDangerColor(theme) : theme.colors.outline}
                />
              </Animated.View>
            ) : null}
          </View>
        </View>
      ) : null}
    </View>
  );
}

const styles = StyleSheet.create({
  bottomControlZone: {
    justifyContent: 'center',
    marginTop: TOKENS.spacing.lg,
  },
  ratingButtonsRow: {
    flexDirection: 'row',
    gap: TOKENS.spacing.sm,
    width: '100%',
  },
  rateButton: {
    flex: 1,
    borderRadius: 100,
  },
  compactContent: {
    justifyContent: 'center',
    paddingLeft: 0,
    paddingRight: 0,
  },
  feedbackContainer: {
    flex: 1,
    justifyContent: 'space-between',
  },
  sttTextWrapper: {
    height: 80,
    justifyContent: 'center',
    alignItems: 'center',
  },
  sttResultBox: {
    padding: TOKENS.spacing.md,
    borderRadius: TOKENS.radius.lg,
    width: '100%',
    alignItems: 'center',
  },
  recognizedText: {
    fontWeight: 'bold',
  },
  audioIconsRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: TOKENS.spacing.xxl,
    height: 60,
  },
  iconWrapper: {
    width: TOKENS.touchTarget.min,
    height: TOKENS.touchTarget.min,
    alignItems: 'center',
    justifyContent: 'center',
  },
  matchPercentText: {
    fontWeight: 'bold',
    marginTop: TOKENS.spacing.xs,
  },
});

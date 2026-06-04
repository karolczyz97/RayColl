import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Button, Text, useTheme } from 'react-native-paper';
import Animated, { FadeIn } from 'react-native-reanimated';
import { AppIcon } from '@/components/AppIcon';
import { usePulseAnimation } from '@/hooks/usePulseAnimation';
import {
  getDangerBgColor,
  getDangerColor,
  getInfoColor,
  getSuccessColor,
  getWarningBgColor,
  getWarningColor,
} from '@/theme/semanticColors';
import { TOKENS } from '@/theme/tokens';

type RatingTone = 'danger' | 'warning' | 'primary' | 'success';

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

interface RatingButtonProps {
  iconName: string;
  isNarrow: boolean;
  label: string;
  onPress: () => void;
  tone: RatingTone;
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
  const ttsIconStyle = usePulseAnimation(isTtsPlaying);
  const sttIconStyle = usePulseAnimation(isSttListening);

  const matchColor =
    sttMatchPercent >= 85
      ? getSuccessColor(theme)
      : sttMatchPercent >= 60
        ? getInfoColor(theme)
        : sttMatchPercent >= 40
          ? getWarningColor(theme)
          : getDangerColor(theme);

  const getRatingButtonColors = (tone: RatingTone) => {
    switch (tone) {
      case 'danger':
        return {
          buttonColor: getDangerBgColor(theme),
          textColor: getDangerColor(theme),
        };
      case 'warning':
        return {
          buttonColor: getWarningBgColor(theme),
          textColor: getWarningColor(theme),
        };
      case 'success':
        return {
          buttonColor: getSuccessColor(theme),
          textColor: theme.colors.onTertiary,
        };
      case 'primary':
      default:
        return {
          buttonColor: undefined,
          textColor: theme.colors.onPrimary,
        };
    }
  };

  const renderRatingButton = ({
    iconName,
    isNarrow: compactButton,
    label,
    onPress,
    tone,
  }: RatingButtonProps) => {
    const colors = getRatingButtonColors(tone);
    const mode = tone === 'danger' || tone === 'warning' ? 'contained-tonal' : 'contained';
    const iconColor = colors.textColor ?? theme.colors.onPrimary;

    return (
      <Button
        mode={mode}
        textColor={colors.textColor}
        buttonColor={colors.buttonColor}
        style={styles.rateButton}
        compact={compactButton}
        contentStyle={compactButton ? styles.compactContent : undefined}
        icon={
          compactButton
            ? undefined
            : ({ size, color }) => <AppIcon name={iconName} size={size} color={color} />
        }
        onPress={onPress}
        accessibilityLabel={label}
      >
        {compactButton ? <AppIcon name={iconName} size={22} color={iconColor} /> : label}
      </Button>
    );
  };

  return (
    <View style={[styles.bottomControlZone, { height: hasTts || hasStt ? TOKENS.layout.studyControlZoneTall : TOKENS.layout.studyControlZoneCompact }]}> 
      {showRatingButtons ? (
        <Animated.View entering={FadeIn.springify()} style={styles.ratingButtonsRow}>
          {renderRatingButton({
            iconName: 'replay',
            isNarrow,
            label: getButtonText('study.rating.1') || ratingLabels[0],
            onPress: () => onRate(1),
            tone: 'danger',
          })}
          {renderRatingButton({
            iconName: 'emoticon-sad-outline',
            isNarrow,
            label: getButtonText('study.rating.2') || ratingLabels[1],
            onPress: () => onRate(2),
            tone: 'warning',
          })}
          {renderRatingButton({
            iconName: 'emoticon-happy-outline',
            isNarrow,
            label: getButtonText('study.rating.3') || ratingLabels[2],
            onPress: () => onRate(3),
            tone: 'primary',
          })}
          {renderRatingButton({
            iconName: 'emoticon-excited-outline',
            isNarrow,
            label: getButtonText('study.rating.4') || ratingLabels[3],
            onPress: () => onRate(4),
            tone: 'success',
          })}
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
                  size={TOKENS.iconSize.lg}
                  color={isTtsPlaying ? theme.colors.primary : theme.colors.outline}
                />
              </Animated.View>
            ) : null}
            {hasStt ? (
              <Animated.View style={[styles.iconWrapper, sttIconStyle]}>
                <AppIcon
                  name="microphone"
                  size={TOKENS.iconSize.lg}
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
    borderRadius: TOKENS.radius.pill,
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
    height: TOKENS.layout.sttTextWrapperHeight,
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
    fontWeight: TOKENS.typography.weight.bold,
  },
  audioIconsRow: {
    flexDirection: 'row',
    justifyContent: 'center',
    alignItems: 'center',
    gap: TOKENS.spacing.xxl,
    height: TOKENS.layout.audioIconsRowHeight,
  },
  iconWrapper: {
    width: TOKENS.touchTarget.min,
    height: TOKENS.touchTarget.min,
    alignItems: 'center',
    justifyContent: 'center',
  },
  matchPercentText: {
    fontWeight: TOKENS.typography.weight.bold,
    marginTop: TOKENS.spacing.xs,
  },
});

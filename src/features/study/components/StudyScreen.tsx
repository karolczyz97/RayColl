import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
import { useI18n } from '@/i18n';
import type { Flashcard, FlashcardGroup } from '@/types/models';
import type { AnswerStatus } from '@/features/study/session/sessionTypes';
import type { SessionProgressItem } from '@/features/study/session/sessionProgress';
import { AppScreen } from '@/components/layout/AppScreen';
import { SegmentedProgressBar } from '@/components/SegmentedProgressBar';
import { StudyCard } from './StudyCard';
import { StudyControls } from './StudyControls';
import { StudyFinishedState } from './StudyFinishedState';
import { AppSnackbar } from '@/components/feedback/AppSnackbar';
import { ConfirmDialog } from '@/components/dialogs/ConfirmDialog';
import { TOKENS } from '@/theme/tokens';

interface StudyScreenProps {
  activeGroup: FlashcardGroup;
  cancelExit: () => void;
  clearError: () => void;
  confirmExit: () => void;
  currentCard: Flashcard | null;
  dueCards: Flashcard[];
  endedEarly: boolean;
  failedCount: number;
  handleBack: () => void;
  handleCardPress: () => void;
  handleRating: (rating: number) => void;
  isNarrow: boolean;
  restartFailed: () => void;
  restartSession: () => void;
  sessionProgressItems: SessionProgressItem[];
  sessionState: {
    currentCardIndex: number;
    revealedPages: number[];
    peekedPageIndex: number | null;
    waitingForTap: boolean;
    audioPageIndex: number | null;
    showRatingButtons: boolean;
    isTtsPlaying: boolean;
    isSttListening: boolean;
    sttResultText: string;
    sttMatchPercent: number;
    sttSuccessThreshold: number | null;
    answerStatus: AnswerStatus;
    isSessionFinished: boolean;
    errorMsg?: string;
  };
  setHolding: (holding: boolean) => void;
  showExitConfirm: boolean;
}

export function StudyScreen({
  activeGroup,
  cancelExit,
  clearError,
  confirmExit,
  currentCard,
  dueCards,
  endedEarly,
  failedCount,
  handleBack,
  handleCardPress,
  handleRating,
  isNarrow,
  restartFailed,
  restartSession,
  sessionProgressItems,
  sessionState,
  setHolding,
  showExitConfirm,
}: StudyScreenProps) {
  const theme = useTheme();
  const { t } = useI18n();
  const isFinished = sessionState.isSessionFinished || dueCards.length === 0;
  const total = dueCards.length;
  const progressLabel = total > 0 ? `${sessionState.currentCardIndex + 1}/${total}` : '0/0';

  return (
    <AppScreen
      title={activeGroup.name}
      onBack={handleBack}
      scroll={false}
      edges={['top', 'left', 'right', 'bottom']}
      headerExtension={
        <View style={styles.headerExtension}>
          <View style={styles.progressBarWrapper}>
            <SegmentedProgressBar
              mode="session"
              items={sessionProgressItems}
              currentIndex={sessionState.currentCardIndex}
              height={TOKENS.control.progressHeight}
            />
          </View>
          <Text style={[styles.progressCounter, { color: theme.colors.onSurfaceVariant }]}>
            {progressLabel}
          </Text>
        </View>
      }
    >
      <View style={styles.content}>
        {isFinished ? (
          <StudyFinishedState
            activeGroup={activeGroup}
            endedEarly={endedEarly}
            failedCount={failedCount}
            dueCardsCount={dueCards.length}
            onRestartFailed={restartFailed}
            onRestartSession={restartSession}
            onBack={handleBack}
          />
        ) : (
          <>
            <StudyCard
              currentCard={currentCard}
              activeGroup={activeGroup}
              revealedPages={sessionState.revealedPages}
              peekedPageIndex={sessionState.peekedPageIndex}
              showRatingButtons={sessionState.showRatingButtons}
              waitingForTap={sessionState.waitingForTap}
              audioPageIndex={sessionState.audioPageIndex}
              audioMode={
                sessionState.isTtsPlaying
                  ? 'speaking'
                  : sessionState.isSttListening
                    ? 'listening'
                    : null
              }
              onCardPress={handleCardPress}
              onHoldingChange={setHolding}
            />
            <StudyControls
              isNarrow={isNarrow}
              isListening={sessionState.isSttListening}
              showRatingButtons={sessionState.showRatingButtons}
              sttResultText={sessionState.sttResultText}
              sttMatchPercent={sessionState.sttMatchPercent}
              sttSuccessThreshold={sessionState.sttSuccessThreshold}
              answerStatus={sessionState.answerStatus}
              onRate={handleRating}
            />
          </>
        )}
      </View>

      <AppSnackbar
        visible={!!sessionState.errorMsg}
        message={t(sessionState.errorMsg || '')}
        onDismiss={clearError}
      />

      <ConfirmDialog
        visible={showExitConfirm}
        title={t('study.exit_confirm_title')}
        message={t('study.exit_confirm_message')}
        confirmLabel={t('study.exit_confirm_btn')}
        cancelLabel={t('btn.cancel')}
        onConfirm={confirmExit}
        onDismiss={cancelExit}
      />
    </AppScreen>
  );
}

const styles = StyleSheet.create({
  headerExtension: {
    marginBottom: TOKENS.spacing.lg,
  },
  progressBarWrapper: {
    marginVertical: TOKENS.spacing.md,
    paddingHorizontal: TOKENS.spacing.lg,
  },
  progressCounter: {
    fontSize: TOKENS.typography.size.xs,
    textAlign: 'right',
    fontWeight: TOKENS.typography.weight.semibold,
    letterSpacing: 0,
    paddingHorizontal: TOKENS.spacing.lg,
  },
  content: {
    flex: 1,
    paddingHorizontal: TOKENS.spacing.lg,
    paddingBottom: TOKENS.spacing.xl,
  },
});

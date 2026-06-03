import React from 'react';
import { StyleSheet, View } from 'react-native';
import { Text, useTheme } from 'react-native-paper';
import type { TranslationFn } from '../../../i18n';
import type { Flashcard, FlashcardGroup } from '../../../types/models';
import type { SessionProgressItem } from '../session/sessionProgress';
import { AppScreen } from '../../../components/layout/AppScreen';
import { SegmentedProgressBar } from '../../../components/SegmentedProgressBar';
import { StudyCard } from './StudyCard';
import { StudyControls } from './StudyControls';
import { StudyFinishedState } from './StudyFinishedState';
import { AppSnackbar } from '../../../components/feedback/AppSnackbar';
import { ConfirmDialog } from '../../../components/dialogs/ConfirmDialog';
import { TOKENS } from '../../../theme/tokens';

interface StudyScreenProps {
  activeGroup: FlashcardGroup;
  cancelExit: () => void;
  clearError: () => void;
  confirmExit: () => void;
  currentCard: Flashcard | null;
  dueCards: Flashcard[];
  failedCount: number;
  getButtonText: (key: string) => string;
  handleBack: () => void;
  handleCardPress: () => void;
  handleRating: (rating: number) => void;
  hasStt: boolean;
  hasTts: boolean;
  isNarrow: boolean;
  restartFailed: () => void;
  restartSession: () => void;
  sessionProgressItems: SessionProgressItem[];
  sessionState: {
    currentCardIndex: number;
    revealedPages: number[];
    peekedPageIndex: number | null;
    waitingForTap: boolean;
    showRatingButtons: boolean;
    isTtsPlaying: boolean;
    isSttListening: boolean;
    sttResultText: string;
    sttMatchPercent: number;
    isSessionFinished: boolean;
    errorMsg?: string;
  };
  setHolding: (holding: boolean) => void;
  showExitConfirm: boolean;
  t: TranslationFn;
}

export function StudyScreen({
  activeGroup,
  cancelExit,
  clearError,
  confirmExit,
  currentCard,
  dueCards,
  failedCount,
  getButtonText,
  handleBack,
  handleCardPress,
  handleRating,
  hasStt,
  hasTts,
  isNarrow,
  restartFailed,
  restartSession,
  sessionProgressItems,
  sessionState,
  setHolding,
  showExitConfirm,
  t,
}: StudyScreenProps) {
  const theme = useTheme();
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
            failedCount={failedCount}
            dueCardsCount={dueCards.length}
            bravoLabel={t('study.bravo')}
            noDueLabel={t('study.no_due')}
            finishedDescription={t('study.finished_desc')}
            deckProgressLabel={t('stats.deck_progress')}
            restartFailedLabel={t('study.restart_failed')}
            restartSessionLabel={t('study.restart_session')}
            backToPanelLabel={t('study.back_to_panel')}
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
              onCardPress={handleCardPress}
              onHoldingChange={setHolding}
              tapToRevealLabel={t('study.tap_to_reveal')}
            />
            <StudyControls
              isNarrow={isNarrow}
              hasTts={hasTts}
              hasStt={hasStt}
              isTtsPlaying={sessionState.isTtsPlaying}
              isSttListening={sessionState.isSttListening}
              showRatingButtons={sessionState.showRatingButtons}
              sttResultText={sessionState.sttResultText}
              sttMatchPercent={sessionState.sttMatchPercent}
              getButtonText={getButtonText}
              ratingLabels={[
                t('study.rating.1'),
                t('study.rating.2'),
                t('study.rating.3'),
                t('study.rating.4'),
              ]}
              recognizedLabel={t('study.recognized')}
              matchPercentLabel={t('study.match_percent', { percent: sessionState.sttMatchPercent })}
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

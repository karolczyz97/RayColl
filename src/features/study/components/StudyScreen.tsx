import React from 'react';
import { StyleSheet } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useTheme } from 'react-native-paper';
import type { TranslationFn } from '../../../i18n';
import type { Flashcard, FlashcardGroup } from '../../../types/models';
import type { SessionProgressItem } from '../session/sessionProgress';
import { StudyStatusBanner } from './StudyStatusBanner';
import { StudyCard } from './StudyCard';
import { StudyControls } from './StudyControls';
import { StudyFinishedState } from './StudyFinishedState';
import { TOKENS } from '../../../theme/tokens';

interface StudyScreenProps {
  activeGroup: FlashcardGroup;
  currentCard: Flashcard | null;
  dueCards: Flashcard[];
  failedCount: number;
  getButtonText: (key: string) => string;
  handleBack: () => void;
  handleCardTap: () => void;
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
    waitingForTap: boolean;
    showRatingButtons: boolean;
    isTtsPlaying: boolean;
    isSttListening: boolean;
    sttResultText: string;
    sttMatchPercent: number;
    isSessionFinished: boolean;
  };
  setHolding: (holding: boolean) => void;
  t: TranslationFn;
}

export function StudyScreen({
  activeGroup,
  currentCard,
  dueCards,
  failedCount,
  getButtonText,
  handleBack,
  handleCardTap,
  handleRating,
  hasStt,
  hasTts,
  isNarrow,
  restartFailed,
  restartSession,
  sessionProgressItems,
  sessionState,
  setHolding,
  t,
}: StudyScreenProps) {
  const theme = useTheme();

  if (sessionState.isSessionFinished || dueCards.length === 0) {
    return (
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
    );
  }

  return (
    <SafeAreaView
      style={[styles.root, { backgroundColor: theme.colors.background }]}
      edges={['top', 'left', 'right', 'bottom']}
    >
      <StudyStatusBanner
        title={activeGroup.name}
        sessionItems={sessionProgressItems}
        currentIndex={sessionState.currentCardIndex}
        progressLabel={`${sessionState.currentCardIndex + 1}/${dueCards.length}`}
        onBack={handleBack}
      />
      <StudyCard
        currentCard={currentCard}
        activeGroup={activeGroup}
        currentCardIndex={sessionState.currentCardIndex}
        revealedPages={sessionState.revealedPages}
        showRatingButtons={sessionState.showRatingButtons}
        waitingForTap={sessionState.waitingForTap}
        onCardTap={handleCardTap}
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
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    paddingHorizontal: TOKENS.spacing.lg,
    paddingTop: 48,
    paddingBottom: TOKENS.spacing.xl,
  },
});

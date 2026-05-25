import { useEffect, useCallback, useMemo, useRef } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import LinearProgress from '@mui/material/LinearProgress';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import VolumeUpIcon from '@mui/icons-material/VolumeUp';
import MicIcon from '@mui/icons-material/Mic';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import TouchAppIcon from '@mui/icons-material/TouchApp';
import ReplayIcon from '@mui/icons-material/Replay';
import { motion } from 'framer-motion';
import { useNav } from '../App';
import { useStudySession } from '../hooks/useStudySession';
import type { Flashcard } from '../types/models';
import { PageHeader } from '../components/PageHeader';
import { GroupNotFound } from '../components/GroupNotFound';
import { useI18n } from '../i18n';
import { SegmentedProgressBar, computeCardStats } from '../components/SegmentedProgressBar';

export function StudyPage() {
  const { nav, goBack, store } = useNav();
  const { t } = useI18n();
  const groupId = nav.params.groupId || '';
  const group = store.groups.find(g => g.id === groupId) || null;

  // Cache the last valid group to prevent blank/fallback screen during exit animation
  const lastValidGroupRef = useRef<typeof group>(null);
  if (group) {
    lastValidGroupRef.current = group;
  }
  const activeGroup = group || lastValidGroupRef.current;

  const mode = store.studyModes.find(m => m.id === activeGroup?.activeModeId) || store.studyModes[0];
  const steps = mode?.steps || [];

  const onCardReviewed = useCallback((gId: string, card: Flashcard) => {
    store.updateFlashcard(gId, card);
    store.recordActivity();
  }, [store]);

  const {
    dueCards, sessionState, handleRating, handleCardTap,
    startSession, stopSession, setHolding, restartSession, restartFailed, failedCount,
  } = useStudySession(activeGroup, steps, onCardReviewed);
  const s = sessionState;
  const currentCard = dueCards[s.currentCardIndex] || null;

  useEffect(() => {
    if (group) {
      const due = store.getDueCards(group.id);
      if (due.length > 0) startSession(due);
    }
  }, [groupId]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleBack = () => { stopSession(); goBack(); };
  const progressPct = dueCards.length > 0 ? ((s.currentCardIndex) / dueCards.length) * 100 : 0;
  const matchColor = s.sttMatchPercent >= 85 ? 'success.main' : s.sttMatchPercent >= 60 ? 'info.main' : s.sttMatchPercent >= 40 ? 'warning.main' : 'error.main';

  // Check if mode uses TTS/STT
  const hasTts = useMemo(() => steps.some(st => st.type === 'speak_page'), [steps]);
  const hasStt = useMemo(() => steps.some(st => st.type === 'listen_and_branch'), [steps]);

  const onPointerDown = useCallback(() => setHolding(true), [setHolding]);
  const onPointerUp = useCallback(() => setHolding(false), [setHolding]);
  const onPointerLeave = useCallback(() => setHolding(false), [setHolding]);

  if (!activeGroup) {
    return <GroupNotFound onBack={goBack} />;
  }

  // Completion screen with restart options
  if (s.isSessionFinished || dueCards.length === 0) return (
    <Box sx={{ p: 3, pb: 12, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '80vh', maxWidth: 600, mx: 'auto' }}>
      <CheckCircleIcon color="success" sx={{ fontSize: 96, mb: 3 }} />
      <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>{t('study.bravo')}</Typography>
      <Typography color="text.secondary" sx={{ mb: 3, textAlign: 'center' }}>
        {dueCards.length === 0 ? t('study.no_due') : t('study.finished_desc')}
      </Typography>

      {/* Progress Bar and stats summary of the deck/group */}
      <Box sx={{ width: '100%', maxWidth: 400, mb: 4, mt: 1 }}>
        <Typography variant="subtitle2" sx={{ fontWeight: 600, mb: 1.5, textAlign: 'center', color: 'text.secondary' }}>
          {t('stats.deck_progress')}
        </Typography>
        <SegmentedProgressBar stats={computeCardStats(activeGroup.cards)} height={14} showLegend />
      </Box>

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, width: '100%', maxWidth: 320 }}>
        {failedCount > 0 && (
          <Button variant="contained" color="warning" size="large" fullWidth
            startIcon={<ReplayIcon />} onClick={restartFailed}>
            {t('study.restart_failed')} ({failedCount})
          </Button>
        )}
        <Button variant="outlined" size="large" fullWidth startIcon={<ReplayIcon />}
          onClick={restartSession}>
          {t('study.restart_session')}
        </Button>
        <Button variant="text" size="large" fullWidth onClick={goBack}>
          {t('study.back_to_panel')}
        </Button>
      </Box>
    </Box>
  );

  return (
    <Box sx={{ p: 3, pb: 12, maxWidth: 600, mx: 'auto', minHeight: '85vh', display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3 }}>
        <PageHeader title={activeGroup.name} onBack={handleBack} />
        <Box sx={{ flexGrow: 1, ml: 2, mr: 2 }}>
          <LinearProgress variant="determinate" value={progressPct} sx={{ height: 6, borderRadius: 3 }} />
        </Box>
        <Typography variant="body2" color="text.secondary">
          {s.currentCardIndex + 1}/{dueCards.length}
        </Typography>
      </Box>

      <Box sx={{ flexGrow: 1, display: 'flex', alignItems: 'stretch', justifyContent: 'center', my: 2, perspective: '1000px' }}>
        <motion.div
          key={s.currentCardIndex}
          initial={{ rotateY: 90, opacity: 0 }}
          animate={{ rotateY: 0, opacity: 1 }}
          whileTap={{ scale: 0.95 }}
          transition={{ type: 'spring', stiffness: 200, damping: 20 }}
          style={{ width: '100%', display: 'flex', flexDirection: 'column', flexGrow: 1 }}
        >
          <Card
            sx={{
              flexGrow: 1,
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'space-between',
              p: 3,
              userSelect: 'none',
              cursor: s.waitingForTap ? 'pointer' : 'default',
              bgcolor: 'background.paper',
              boxShadow: '0 8px 32px rgba(0,0,0,0.06)',
              border: '1px solid',
              borderColor: 'divider',
              borderRadius: 4,
              '&:hover': s.waitingForTap ? { boxShadow: 8 } : {},
              transition: 'box-shadow 0.2s',
            }}
            onClick={s.waitingForTap ? handleCardTap : undefined}
            onPointerDown={onPointerDown}
            onPointerUp={onPointerUp}
            onPointerLeave={onPointerLeave}
          >
            <CardContent sx={{ display: 'flex', flexDirection: 'column', height: '100%', flexGrow: 1, p: 0, '&:last-child': { pb: 0 }, position: 'relative' }}>
              {currentCard && Array.from({ length: activeGroup.pageNames.length }).map((_, pi) => {
                const isRevealed = s.revealedPages.includes(pi);
                const pageLabel = activeGroup.pageNames[pi] || t('import.page_label', { index: pi + 1 });
                const cardText = currentCard.pages[pi] || '—';

                return (
                  <Box key={pi} sx={{
                    flex: '1 1 0px',
                    width: '100%',
                    borderTop: pi > 0 ? '1px solid' : 'none',
                    borderColor: 'divider',
                    position: 'relative',
                    perspective: '1000px',
                  }}>
                    <motion.div
                      initial={false}
                      animate={{ rotateX: isRevealed ? 180 : 0 }}
                      transition={{ type: 'spring', stiffness: 140, damping: 18 }}
                      style={{
                        width: '100%',
                        height: '100%',
                        position: 'relative',
                        transformStyle: 'preserve-3d',
                      }}
                    >
                      {/* Card Front (Unrevealed: Page Name) */}
                      <Box
                        sx={{
                          position: 'absolute',
                          inset: 0,
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'center',
                          justifyContent: 'center',
                          backfaceVisibility: 'hidden',
                          WebkitBackfaceVisibility: 'hidden',
                          px: 2,
                        }}
                      >
                        <Typography
                          variant="h5"
                          sx={{
                            fontWeight: 600,
                            color: 'text.disabled',
                            textTransform: 'uppercase',
                            letterSpacing: 1.5,
                            textAlign: 'center',
                            opacity: 0.6,
                          }}
                        >
                          {pageLabel}
                        </Typography>
                      </Box>

                      {/* Card Back (Revealed: Content) */}
                      <Box
                        sx={{
                          position: 'absolute',
                          inset: 0,
                          display: 'flex',
                          flexDirection: 'column',
                          alignItems: 'center',
                          justifyContent: 'center',
                          backfaceVisibility: 'hidden',
                          WebkitBackfaceVisibility: 'hidden',
                          transform: 'rotateX(180deg)',
                          px: 2,
                        }}
                      >
                        <Typography
                          variant={pi === 0 ? "h4" : "h5"}
                          sx={{
                            fontWeight: pi === 0 ? 600 : 500,
                            textAlign: 'center',
                            color: pi === 0 ? 'primary.main' : 'text.primary',
                          }}
                        >
                          {cardText}
                        </Typography>
                      </Box>
                    </motion.div>
                  </Box>
                );
              })}

              {/* Tap Indicator */}
              {s.waitingForTap && (
                <Box sx={{ position: 'absolute', bottom: 12, left: 0, right: 0, display: 'flex', justifyContent: 'center', pointerEvents: 'none' }}>
                  <motion.div initial={{ opacity: 0 }} animate={{ opacity: 0.6 }}
                    transition={{ delay: 0.5, duration: 0.5 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.8, bgcolor: 'background.paper', px: 1.5, py: 0.5, borderRadius: 2, border: '1px solid', borderColor: 'divider' }}>
                      <TouchAppIcon fontSize="small" color="action" />
                      <Typography variant="caption" color="text.disabled" sx={{ fontWeight: 600 }}>{t('study.tap_to_reveal')}</Typography>
                    </Box>
                  </motion.div>
                </Box>
              )}
            </CardContent>
          </Card>
        </motion.div>
      </Box>

      {/* Bottom controls container with reserved height to prevent card height shifts */}
      <Box sx={{
        height: (hasTts || hasStt) ? 160 : 64,
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: 'stretch',
        mt: 2
      }}>
        {s.showRatingButtons ? (
          <motion.div initial={{ y: 20, opacity: 0 }} animate={{ y: 0, opacity: 1 }}>
            <Box sx={{ display: 'flex', gap: 1 }}>
              <Button variant="outlined" color="error" fullWidth onClick={() => handleRating(1)}>{t('study.rating.1')}</Button>
              <Button variant="outlined" color="warning" fullWidth onClick={() => handleRating(2)}>{t('study.rating.2')}</Button>
              <Button variant="contained" color="primary" fullWidth onClick={() => handleRating(3)}>{t('study.rating.3')}</Button>
              <Button variant="contained" color="success" fullWidth onClick={() => handleRating(4)}>{t('study.rating.4')}</Button>
            </Box>
          </motion.div>
        ) : (hasTts || hasStt) ? (
          <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            {/* Top area for STT Result (fixed height to prevent icon shift) */}
            <Box sx={{ height: 100, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              {s.sttResultText ? (
                <motion.div initial={{ y: 10, opacity: 0 }} animate={{ y: 0, opacity: 1 }} style={{ width: '100%' }}>
                  <Box sx={{ bgcolor: 'action.hover', p: 1.5, borderRadius: 2, textAlign: 'center', width: '100%' }}>
                    <Typography variant="caption" color="text.secondary">{t('study.recognized')}</Typography>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>{s.sttResultText}</Typography>
                    {s.sttMatchPercent > 0 && (
                      <Typography variant="caption" sx={{ color: matchColor, fontWeight: 700, display: 'block', mt: 0.5 }}>
                        {t('study.match_percent', { percent: s.sttMatchPercent })}
                      </Typography>
                    )}
                  </Box>
                </motion.div>
              ) : null}
            </Box>

            {/* Bottom area for icons */}
            <Box sx={{ height: 60, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 3 }}>
              {hasTts && (
                <motion.div animate={s.isTtsPlaying ? { scale: [1, 1.3, 1] } : {}}
                  transition={{ repeat: Infinity, duration: 0.8 }}>
                  <VolumeUpIcon color={s.isTtsPlaying ? 'primary' : 'disabled'} sx={{ fontSize: 36 }} />
                </motion.div>
              )}
              {hasStt && (
                <motion.div animate={s.isSttListening ? { scale: [1, 1.3, 1] } : {}}
                  transition={{ repeat: Infinity, duration: 0.8 }}>
                  <MicIcon color={s.isSttListening ? 'error' : 'disabled'} sx={{ fontSize: 36 }} />
                </motion.div>
              )}
            </Box>
          </Box>
        ) : null}
      </Box>
    </Box>
  );
}

import { useEffect, useCallback, useMemo, useRef } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import LinearProgress from '@mui/material/LinearProgress';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import VolumeUpIcon from '@mui/icons-material/VolumeUp';
import MicIcon from '@mui/icons-material/Mic';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import TouchAppIcon from '@mui/icons-material/TouchApp';
import ReplayIcon from '@mui/icons-material/Replay';
import { motion } from 'framer-motion';
import { useNav } from '../App';
import { useStudySession } from '../hooks/useStudySession';
import type { Flashcard } from '../types/models';

export function StudyPage() {
  const { nav, goBack, store } = useNav();
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
  const matchColor = s.sttMatchPercent >= 70 ? 'success.main' : s.sttMatchPercent >= 50 ? 'warning.main' : 'error.main';

  // Check if mode uses TTS/STT
  const hasTts = useMemo(() => steps.some(st => st.type === 'speak_page'), [steps]);
  const hasStt = useMemo(() => steps.some(st => st.type === 'listen_and_branch'), [steps]);

  const onPointerDown = useCallback(() => setHolding(true), [setHolding]);
  const onPointerUp = useCallback(() => setHolding(false), [setHolding]);
  const onPointerLeave = useCallback(() => setHolding(false), [setHolding]);

  if (!activeGroup) return (
    <Box sx={{ p: 4, textAlign: 'center' }}>
      <Typography>Nie znaleziono grupy.</Typography>
      <Button onClick={goBack} sx={{ mt: 2 }}>Powrót</Button>
    </Box>
  );

  // Completion screen with restart options
  if (s.isSessionFinished || dueCards.length === 0) return (
    <Box sx={{ p: 4, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', minHeight: '80vh' }}>
      <CheckCircleIcon color="success" sx={{ fontSize: 96, mb: 3 }} />
      <Typography variant="h4" sx={{ fontWeight: 700, mb: 1 }}>Brawo! 🎉</Typography>
      <Typography color="text.secondary" sx={{ mb: 4 }}>
        {dueCards.length === 0 ? 'Brak fiszek do powtórki!' : 'Wszystkie fiszki powtórzone.'}
      </Typography>
      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, width: '100%', maxWidth: 320 }}>
        {failedCount > 0 && (
          <Button variant="contained" color="warning" size="large" fullWidth
            startIcon={<ReplayIcon />} onClick={restartFailed}>
            Powtórz trudne ({failedCount})
          </Button>
        )}
        <Button variant="outlined" size="large" fullWidth startIcon={<ReplayIcon />}
          onClick={restartSession}>
          Powtórz wszystkie
        </Button>
        <Button variant="text" size="large" fullWidth onClick={goBack}>
          Powrót do panelu
        </Button>
      </Box>
    </Box>
  );

  return (
    <Box sx={{ p: 3, maxWidth: 600, mx: 'auto', minHeight: '85vh', display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3 }}>
        <IconButton onClick={handleBack}><ArrowBackIcon /></IconButton>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{activeGroup.name}</Typography>
          <LinearProgress variant="determinate" value={progressPct} sx={{ height: 6, borderRadius: 3, mt: 0.5 }} />
        </Box>
        <Typography variant="body2" color="text.secondary">
          {s.currentCardIndex + 1}/{dueCards.length}
        </Typography>
      </Box>

      <Box sx={{ flexGrow: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', my: 3, perspective: '1000px' }}>
        <motion.div
          key={s.currentCardIndex}
          initial={{ rotateY: 90, opacity: 0 }}
          animate={{ rotateY: 0, opacity: 1 }}
          transition={{ type: 'spring', stiffness: 200, damping: 20 }}
          style={{ width: '100%' }}
        >
          <Card
            sx={{
              minHeight: 260, display: 'flex', flexDirection: 'column',
              justifyContent: 'center', p: 3, userSelect: 'none',
              cursor: s.waitingForTap ? 'pointer' : 'default',
              '&:hover': s.waitingForTap ? { transform: 'scale(1.01)', boxShadow: 6 } : {},
              transition: 'all 0.2s',
            }}
            onClick={s.waitingForTap ? handleCardTap : undefined}
            onPointerDown={onPointerDown}
            onPointerUp={onPointerUp}
            onPointerLeave={onPointerLeave}
          >
            <CardContent sx={{ textAlign: 'center' }}>
              {currentCard && s.revealedPages.map(pi => (
                <Box key={pi} sx={{ mb: 2 }}>
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                    {activeGroup.pageNames[pi] || `Strona ${pi + 1}`}
                  </Typography>
                  <Typography variant="h4" sx={{ fontWeight: 500 }}>
                    {currentCard.pages[pi] || '—'}
                  </Typography>
                </Box>
              ))}
              {s.waitingForTap && (
                <motion.div initial={{ opacity: 0 }} animate={{ opacity: 0.5 }}
                  transition={{ delay: 1, duration: 0.5 }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 1, mt: 2 }}>
                    <TouchAppIcon color="action" />
                    <Typography variant="caption" color="text.disabled">Dotknij, aby odkryć</Typography>
                  </Box>
                </motion.div>
              )}
            </CardContent>
          </Card>
        </motion.div>
      </Box>

      {/* Phase indicators — only for modes that use TTS/STT */}
      {(hasTts || hasStt) && (
        <Box sx={{ display: 'flex', justifyContent: 'center', gap: 3, mb: 2 }}>
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
      )}

      {s.sttResultText && (
        <motion.div initial={{ y: 20, opacity: 0 }} animate={{ y: 0, opacity: 1 }}>
          <Box sx={{ bgcolor: 'action.hover', p: 2, borderRadius: 2, mb: 2, textAlign: 'center' }}>
            <Typography variant="caption" color="text.secondary">Rozpoznano:</Typography>
            <Typography sx={{ fontWeight: 600 }}>{s.sttResultText}</Typography>
            {s.sttMatchPercent > 0 && (
              <Typography sx={{ color: matchColor, fontWeight: 700, mt: 0.5 }}>
                Dopasowanie: {s.sttMatchPercent}%
              </Typography>
            )}
          </Box>
        </motion.div>
      )}

      {s.showRatingButtons && (
        <motion.div initial={{ y: 30, opacity: 0 }} animate={{ y: 0, opacity: 1 }}>
          <Box sx={{ display: 'flex', gap: 1, mt: 'auto' }}>
            <Button variant="outlined" color="error" fullWidth onClick={() => handleRating(1)}>Powtórz</Button>
            <Button variant="outlined" color="warning" fullWidth onClick={() => handleRating(3)}>Trudne</Button>
            <Button variant="contained" color="primary" fullWidth onClick={() => handleRating(4)}>Dobrze</Button>
            <Button variant="contained" color="success" fullWidth onClick={() => handleRating(5)}>Łatwe</Button>
          </Box>
        </motion.div>
      )}
    </Box>
  );
}

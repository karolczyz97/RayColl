import { useState, useMemo, useRef } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import TextField from '@mui/material/TextField';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardActions from '@mui/material/CardActions';
import Fab from '@mui/material/Fab';
import Stack from '@mui/material/Stack';
import Chip from '@mui/material/Chip';
import Tooltip from '@mui/material/Tooltip';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import AddIcon from '@mui/icons-material/Add';
import EditIcon from '@mui/icons-material/Edit';
import DeleteIcon from '@mui/icons-material/Delete';
import ThumbUpIcon from '@mui/icons-material/ThumbUp';
import LoopIcon from '@mui/icons-material/Loop';
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import { useNav } from '../App';
import { SegmentedProgressBar, computeCardStats } from '../components/SegmentedProgressBar';
import type { Flashcard, SrsState } from '../types/models';
import { getCardCategory, CATEGORIES } from '../srs/srsEngine';

type BrowseFilter = 'all' | 'learning' | 'review' | 'new' | 'mastered';

function srsChip(state: SrsState): { text: string; color: 'default' | 'info' | 'warning' | 'success' | 'secondary' } {
  const category = getCardCategory(state);
  const info = CATEGORIES[category];
  return {
    text: info.badgeText,
    color: info.chipColor,
  };
}

function daysUntilReview(state: SrsState): string {
  if (state.state === 0) return 'Nowa';
  const diff = state.nextReviewTimestamp - Date.now();
  if (diff <= 0) return 'Teraz';
  const days = Math.ceil(diff / (1000 * 60 * 60 * 24));
  return `${days}d`;
}

function masteryPercent(state: SrsState): number {
  if (state.state === 0) return 0;
  return Math.min(100, Math.round(Math.min(state.stability / 30, 1) * 100));
}

export function BrowsePage() {
  const { nav, goBack, store } = useNav();
  const groupId = nav.params.groupId || '';
  const group = store.groups.find(g => g.id === groupId);

  // Cache the last valid group to prevent blank/fallback screen during exit animation
  const lastValidGroupRef = useRef<typeof group>(undefined);
  if (group) {
    lastValidGroupRef.current = group;
  }
  const activeGroup = group || lastValidGroupRef.current;

  const [search, setSearch] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editPages, setEditPages] = useState<string[]>([]);
  const [deleteCardId, setDeleteCardId] = useState<string | null>(null);
  const [browseFilter, setBrowseFilter] = useState<BrowseFilter>('all');

  const stats = useMemo(() => activeGroup ? computeCardStats(activeGroup.cards) : { total: 0, newCount: 0, learning: 0, review: 0, mastered: 0 }, [activeGroup]);

  const filtered = useMemo(() => {
    if (!activeGroup) return [];
    let cards: Flashcard[];
    // Filter by SRS STATE, not due date — so user sees all cards in that category
    switch (browseFilter) {
      case 'new': cards = activeGroup.cards.filter(c => getCardCategory(c.srsState) === 'new'); break;
      case 'learning': cards = activeGroup.cards.filter(c => getCardCategory(c.srsState) === 'learning'); break;
      case 'review': cards = activeGroup.cards.filter(c => getCardCategory(c.srsState) === 'review'); break;
      case 'mastered': cards = activeGroup.cards.filter(c => getCardCategory(c.srsState) === 'mastered'); break;
      case 'all': default: cards = [...activeGroup.cards]; break;
    }
    const q = search.toLowerCase();
    if (q) cards = cards.filter(c => c.pages.some(p => p.toLowerCase().includes(q)));
    return cards;
  }, [activeGroup, search, browseFilter]);

  const startEdit = (card: Flashcard) => { setEditingId(card.id); setEditPages([...card.pages]); };
  const cancelEdit = () => { setEditingId(null); setEditPages([]); };
  const saveEdit = () => {
    if (!editingId || !activeGroup) return;
    const filledCount = editPages.filter(p => p.trim()).length;
    // Need at least 2 filled pages, otherwise delete
    if (filledCount < 2) {
      store.deleteFlashcard(activeGroup.id, editingId);
      cancelEdit(); return;
    }
    const card = activeGroup.cards.find(c => c.id === editingId);
    if (card) store.updateFlashcard(activeGroup.id, { ...card, pages: editPages });
    cancelEdit();
  };
  const addCard = () => {
    if (!activeGroup) return;
    const pages = activeGroup.pageNames.map(() => '');
    const id = store.addFlashcard(activeGroup.id, pages);
    setEditingId(id); setEditPages(pages);
  };

  if (!activeGroup) return (
    <Box sx={{ p: 4 }}><Typography>Grupa nie znaleziona.</Typography><Button onClick={goBack}>Powrót</Button></Box>
  );

  return (
    <Box sx={{ p: 3, pb: 12, maxWidth: 800, mx: 'auto' }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
        <IconButton onClick={goBack}><ArrowBackIcon /></IconButton>
        <Typography variant="h5" sx={{ fontWeight: 700, flexGrow: 1 }}>{activeGroup.name}</Typography>
        <Typography variant="body2" color="text.secondary">{stats.total} fiszek</Typography>
      </Box>

      {/* Segmented progress bar */}
      <Box sx={{ mb: 3 }}>
        <SegmentedProgressBar stats={stats} height={14} showLegend />
      </Box>

      <TextField fullWidth label="Szukaj fiszek..." variant="outlined" value={search}
        onChange={e => setSearch(e.target.value)} sx={{ mb: 2 }} />

      {/* Filter chips — colors match the progress bar segments */}
      <Box sx={{ display: 'flex', gap: 1, mb: 3, flexWrap: 'wrap' }}>
        <Chip label={`Wszystkie (${stats.total})`} variant={browseFilter === 'all' ? 'filled' : 'outlined'}
          color={browseFilter === 'all' ? 'primary' : 'default'} clickable onClick={() => setBrowseFilter('all')} />
        <Chip label={`Uczone (${stats.learning})`} variant={browseFilter === 'learning' ? 'filled' : 'outlined'}
          color={browseFilter === 'learning' ? 'warning' : 'default'} clickable onClick={() => setBrowseFilter('learning')} />
        <Chip label={`Powtórki (${stats.review})`} variant={browseFilter === 'review' ? 'filled' : 'outlined'}
          color={browseFilter === 'review' ? 'success' : 'default'} clickable onClick={() => setBrowseFilter('review')} />
        <Chip label={`Nowe (${stats.newCount})`} variant={browseFilter === 'new' ? 'filled' : 'outlined'}
          color={browseFilter === 'new' ? 'info' : 'default'} clickable onClick={() => setBrowseFilter('new')} />
        <Chip label={`Opanowane (${stats.mastered})`} variant={browseFilter === 'mastered' ? 'filled' : 'outlined'}
          color={browseFilter === 'mastered' ? 'secondary' : 'default'} clickable onClick={() => setBrowseFilter('mastered')} />
      </Box>

      <Stack spacing={2}>
        {filtered.map(card => {
          const srs = srsChip(card.srsState);
          const mastery = masteryPercent(card.srsState);
          const reviewIn = daysUntilReview(card.srsState);
          return (
            <Card key={card.id} sx={editingId === card.id ? { borderLeft: '4px solid', borderColor: 'primary.main' } : {}}>
              {editingId === card.id ? (
                <>
                  <CardContent>
                    <Stack spacing={2}>
                      {editPages.map((page, i) => (
                        <TextField key={i} fullWidth label={activeGroup.pageNames[i] || `Strona ${i + 1}`}
                          value={page} onChange={e => {
                            const next = [...editPages]; next[i] = e.target.value; setEditPages(next);
                          }} />
                      ))}
                    </Stack>
                  </CardContent>
                  <CardActions sx={{ justifyContent: 'flex-end', gap: 1, px: 2, pb: 2 }}>
                    <Button onClick={cancelEdit}>Anuluj</Button>
                    <Button variant="contained" onClick={saveEdit}>Zapisz</Button>
                  </CardActions>
                </>
              ) : (
                <>
                  <CardContent sx={{ pb: 0 }}>
                    <Stack spacing={0.5}>
                      {card.pages.map((page, i) => (
                        <Box key={i}>
                          <Typography variant="caption" color="text.secondary">
                            {activeGroup.pageNames[i] || `Strona ${i + 1}`}:
                          </Typography>
                          <Typography variant="body1" sx={{ fontWeight: i === 0 ? 600 : 400 }}>{page}</Typography>
                        </Box>
                      ))}
                    </Stack>
                  </CardContent>
                  <CardActions sx={{ justifyContent: 'space-between', px: 2, py: 1 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                      <Chip label={srs.text} color={srs.color} size="small" />
                      {card.srsState.state > 0 && (
                        <>
                          <Tooltip title={`Opanowanie: ${mastery}%`}>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.3 }}>
                              <ThumbUpIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                              <Typography variant="caption" color="text.secondary">{mastery}%</Typography>
                            </Box>
                          </Tooltip>
                          <Tooltip title={`Powtórzeń: ${card.srsState.repetitions}`}>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.3 }}>
                              <LoopIcon sx={{ fontSize: 16, color: 'text.secondary' }} />
                              <Typography variant="caption" color="text.secondary">{card.srsState.repetitions}</Typography>
                            </Box>
                          </Tooltip>
                          <Tooltip title={`Powtórka za: ${reviewIn}`}>
                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.3 }}>
                              <CalendarTodayIcon sx={{ fontSize: 14, color: 'text.secondary' }} />
                              <Typography variant="caption" color="text.secondary">{reviewIn}</Typography>
                            </Box>
                          </Tooltip>
                        </>
                      )}
                    </Box>
                    <Box sx={{ display: 'flex' }}>
                      <IconButton color="primary" size="small" onClick={() => startEdit(card)}><EditIcon fontSize="small" /></IconButton>
                      <IconButton color="error" size="small" onClick={() => setDeleteCardId(card.id)}><DeleteIcon fontSize="small" /></IconButton>
                    </Box>
                  </CardActions>
                </>
              )}
            </Card>
          );
        })}
      </Stack>

      <Fab color="primary" sx={{ position: 'fixed', bottom: 24, right: 24 }} onClick={addCard}><AddIcon /></Fab>

      <Dialog open={!!deleteCardId} onClose={() => setDeleteCardId(null)}>
        <DialogTitle>Usuń fiszkę</DialogTitle>
        <DialogContent>
          <Typography>Czy na pewno chcesz usunąć tę fiszkę?</Typography>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDeleteCardId(null)}>Anuluj</Button>
          <Button variant="contained" color="error" onClick={() => { if (deleteCardId) { store.deleteFlashcard(groupId, deleteCardId); setDeleteCardId(null); } }}>Usuń</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

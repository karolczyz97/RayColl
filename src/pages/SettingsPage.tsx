import { useState, useRef } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemText from '@mui/material/ListItemText';
import MenuItem from '@mui/material/MenuItem';
import TextField from '@mui/material/TextField';
import Select from '@mui/material/Select';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Divider from '@mui/material/Divider';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import { useNav } from '../App';
import type { ModeStep, StudyMode } from '../types/models';

const STEP_LABELS: Record<string, string> = {
  show_page: 'Pokaż stronę', speak_page: 'Wymawiaj (TTS)', dynamic_pause: 'Pauza dynamiczna',
  wait: 'Odczekaj (ms)', listen_and_branch: 'Weryfikuj wymowę (STT)',
};
const POPULAR_LANGS = [
  { code: 'pl-PL', label: 'Polski' }, { code: 'en-US', label: 'Angielski' },
  { code: 'es-ES', label: 'Hiszpański' }, { code: 'de-DE', label: 'Niemiecki' },
  { code: 'fr-FR', label: 'Francuski' }, { code: 'it-IT', label: 'Włoski' },
  { code: 'pt-PT', label: 'Portugalski' }, { code: 'ru-RU', label: 'Rosyjski' },
  { code: 'ja-JP', label: 'Japoński' }, { code: 'zh-CN', label: 'Chiński' },
];
const DEFAULT_MODE_IDS = ['classic', 'listen-speak'];

function stepSummary(step: ModeStep): string {
  switch (step.type) {
    case 'show_page': return `Pokaż stronę ${step.pageIndex + 1}`;
    case 'speak_page': return `TTS strona ${step.pageIndex + 1} (+${step.extraPauseMs}ms)`;
    case 'dynamic_pause': return `Pauza dyn. (str. ${step.nextPageIndex + 1}, +${step.extraPauseMs}ms)`;
    case 'wait': return `Odczekaj ${step.ms}ms`;
    case 'listen_and_branch': return `STT strona ${step.pageIndex + 1} (próg ${step.successThreshold}%)`;
  }
}

export function SettingsPage() {
  const { nav, goBack, store } = useNav();
  const groupId = nav.params.groupId || '';
  const group = store.groups.find(g => g.id === groupId);

  // Cache the last valid group to prevent blank/fallback screen during exit animation
  const lastValidGroupRef = useRef<typeof group>(undefined);
  if (group) {
    lastValidGroupRef.current = group;
  }
  const activeGroup = group || lastValidGroupRef.current;

  // Delete confirmation
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
  const [deleteConfirmText, setDeleteConfirmText] = useState('');

  // Step add dialog
  const [stepDialogOpen, setStepDialogOpen] = useState(false);
  const [newStepType, setNewStepType] = useState<string>('show_page');
  const [newPageIdx, setNewPageIdx] = useState(0);
  const [newMs, setNewMs] = useState(500);
  const [newThreshold, setNewThreshold] = useState(70);

  // Create mode
  const [creatingMode, setCreatingMode] = useState(false);
  const [newModeName, setNewModeName] = useState('');
  const [customSteps, setCustomSteps] = useState<ModeStep[]>([]);

  // Editing existing custom mode
  const [editingModeId, setEditingModeId] = useState<string | null>(null);

  // Page config — preserve removed pages for restore
  const removedNamesRef = useRef<string[]>([]);
  const removedLangsRef = useRef<string[]>([]);

  if (!activeGroup) return (
    <Box sx={{ p: 4 }}><Typography>Grupa nie znaleziona.</Typography><Button onClick={goBack}>Powrót</Button></Box>
  );

  const activeMode = store.studyModes.find(m => m.id === activeGroup.activeModeId);
  const isDefaultMode = DEFAULT_MODE_IDS.includes(activeMode?.id || '');

  // Group name
  const handleRename = (newName: string) => {
    store.updateGroup({ ...activeGroup, name: newName });
  };

  const pageCount = activeGroup.pageNames.length;
  const setPageCount = (count: number) => {
    if (count < pageCount) {
      // Store removed pages
      removedNamesRef.current = activeGroup.pageNames.slice(count);
      removedLangsRef.current = activeGroup.pageLanguages.slice(count);
    }
    const names = [...activeGroup.pageNames];
    const langs = [...activeGroup.pageLanguages];
    while (names.length < count) {
      // Restore from removed first, else default
      const restoreIdx = names.length - pageCount;
      if (restoreIdx >= 0 && restoreIdx < removedNamesRef.current.length) {
        names.push(removedNamesRef.current[restoreIdx]);
        langs.push(removedLangsRef.current[restoreIdx] || 'en-US');
      } else {
        names.push(`Strona ${names.length + 1}`);
        langs.push('en-US');
      }
    }
    store.updateGroup({ ...activeGroup, pageNames: names.slice(0, count), pageLanguages: langs.slice(0, count) });
  };
  const updatePN = (i: number, v: string) => {
    const names = [...activeGroup.pageNames]; names[i] = v;
    store.updateGroup({ ...activeGroup, pageNames: names });
  };
  const updatePL = (i: number, v: string) => {
    const langs = [...activeGroup.pageLanguages]; langs[i] = v;
    store.updateGroup({ ...activeGroup, pageLanguages: langs });
  };
  const movePageSetting = (i: number, dir: -1 | 1) => {
    const j = i + dir; if (j < 0 || j >= pageCount) return;
    const nn = [...activeGroup.pageNames]; [nn[i], nn[j]] = [nn[j], nn[i]];
    const nl = [...activeGroup.pageLanguages]; [nl[i], nl[j]] = [nl[j], nl[i]];
    // Also swap pages in all cards
    const updatedCards = activeGroup.cards.map(card => {
      const pages = [...card.pages];
      [pages[i], pages[j]] = [pages[j], pages[i]];
      return { ...card, pages };
    });
    store.updateGroup({ ...activeGroup, pageNames: nn, pageLanguages: nl, cards: updatedCards });
  };

  // Mode steps reordering (custom modes only)
  const moveStep = (mode: StudyMode, i: number, dir: -1 | 1) => {
    const j = i + dir; if (j < 0 || j >= mode.steps.length) return;
    const steps = [...mode.steps]; [steps[i], steps[j]] = [steps[j], steps[i]];
    store.addStudyMode({ ...mode, steps }); // addStudyMode will update since we pass same id? No, we need updateStudyMode
    // Workaround: delete and re-add
    store.deleteStudyMode(mode.id);
    store.addStudyMode({ ...mode, steps });
  };

  const addStepToMode = (mode: StudyMode) => {
    setEditingModeId(mode.id);
    setStepDialogOpen(true);
  };

  const confirmAddStep = () => {
    let step: ModeStep;
    switch (newStepType) {
      case 'show_page': step = { type: 'show_page', pageIndex: newPageIdx }; break;
      case 'speak_page': step = { type: 'speak_page', pageIndex: newPageIdx, extraPauseMs: newMs }; break;
      case 'dynamic_pause': step = { type: 'dynamic_pause', nextPageIndex: newPageIdx, extraPauseMs: newMs }; break;
      case 'wait': step = { type: 'wait', ms: newMs }; break;
      case 'listen_and_branch': step = { type: 'listen_and_branch', pageIndex: newPageIdx, successThreshold: newThreshold }; break;
      default: return;
    }

    if (editingModeId && !creatingMode) {
      const mode = store.studyModes.find(m => m.id === editingModeId);
      if (mode) {
        store.deleteStudyMode(mode.id);
        store.addStudyMode({ ...mode, steps: [...mode.steps, step] });
      }
    } else {
      setCustomSteps(s => [...s, step]);
    }
    setStepDialogOpen(false);
    setEditingModeId(null);
  };

  const saveCustomMode = () => {
    if (!newModeName.trim() || customSteps.length === 0) return;
    const mode: StudyMode = { id: crypto.randomUUID(), name: newModeName.trim(), steps: customSteps };
    store.addStudyMode(mode);
    store.updateGroup({ ...activeGroup, activeModeId: mode.id });
    setCreatingMode(false); setCustomSteps([]); setNewModeName('');
  };

  const handleDelete = () => {
    if (deleteConfirmText === 'DELETE') {
      store.deleteGroup(groupId); setDeleteDialogOpen(false); goBack();
    }
  };

  return (
    <Box sx={{ p: 3, maxWidth: 600, mx: 'auto' }}>
      {/* Top bar */}
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3 }}>
        <IconButton onClick={goBack}><ArrowBackIcon /></IconButton>
        <Typography variant="h5" sx={{ fontWeight: 700, flexGrow: 1 }}>Ustawienia zestawu</Typography>
        <IconButton color="error" onClick={() => setDeleteDialogOpen(true)}><DeleteIcon /></IconButton>
      </Box>

      {/* Group name */}
      <TextField fullWidth label="Nazwa zestawu" value={activeGroup.name}
        onChange={e => handleRename(e.target.value)} sx={{ mb: 3 }} />

      <Divider sx={{ mb: 3 }} />

      {/* Page configuration */}
      <Typography variant="h6" sx={{ mb: 2 }}>Konfiguracja stron</Typography>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2 }}>
        <Typography>Liczba stron:</Typography>
        <IconButton size="small" onClick={() => setPageCount(pageCount - 1)} disabled={pageCount <= 2}><RemoveIcon /></IconButton>
        <Typography variant="h6" sx={{ fontWeight: 700, minWidth: 32, textAlign: 'center' }}>{pageCount}</Typography>
        <IconButton size="small" onClick={() => setPageCount(pageCount + 1)} disabled={pageCount >= 5}><AddIcon /></IconButton>
      </Box>
      {activeGroup.pageNames.map((pn, i) => (
        <Box key={i} sx={{ display: 'flex', gap: 1, alignItems: 'center', mb: 1.5 }}>
          <Box sx={{ display: 'flex', flexDirection: 'column' }}>
            <IconButton size="small" onClick={() => movePageSetting(i, -1)} disabled={i === 0}><ArrowUpwardIcon fontSize="small" /></IconButton>
            <IconButton size="small" onClick={() => movePageSetting(i, 1)} disabled={i === pageCount - 1}><ArrowDownwardIcon fontSize="small" /></IconButton>
          </Box>
          <TextField label={`Strona ${i + 1}`} value={pn} onChange={e => updatePN(i, e.target.value)} fullWidth size="small" />
          <TextField select label="Język" value={activeGroup.pageLanguages[i] || 'en-US'}
            onChange={e => updatePL(i, e.target.value)} sx={{ minWidth: 140 }} size="small">
            {POPULAR_LANGS.map(l => <MenuItem key={l.code} value={l.code}>{l.label}</MenuItem>)}
          </TextField>
        </Box>
      ))}

      <Divider sx={{ my: 3 }} />

      {/* Mode selection */}
      <Typography variant="h6" sx={{ mb: 2 }}>Aktywny tryb nauki</Typography>
      <FormControl fullWidth sx={{ mb: 3 }}>
        <InputLabel>Tryb</InputLabel>
        <Select value={activeGroup.activeModeId} label="Tryb"
          onChange={e => store.updateGroup({ ...activeGroup, activeModeId: e.target.value as string })}>
          {store.studyModes.map(m => <MenuItem key={m.id} value={m.id}>{m.name}</MenuItem>)}
        </Select>
      </FormControl>

      {/* Study filter */}
      <Typography variant="h6" sx={{ mb: 2 }}>Zakres nauki</Typography>
      <FormControl fullWidth sx={{ mb: 3 }}>
        <InputLabel>Które fiszki uczyć</InputLabel>
        <Select value={activeGroup.studyFilter || 'new+review'} label="Które fiszki uczyć"
          onChange={e => store.updateGroup({ ...activeGroup, studyFilter: e.target.value as any })}>
          <MenuItem value="new+review">Nowe + do powtórki</MenuItem>
          <MenuItem value="new">Tylko nowe</MenuItem>
          <MenuItem value="review">Tylko do powtórki</MenuItem>
          <MenuItem value="all">Wszystkie</MenuItem>
        </Select>
      </FormControl>

      {/* Active mode steps */}
      {activeMode && (
        <Box sx={{ mb: 3 }}>
          <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 1 }}>
            Kroki trybu „{activeMode.name}" {isDefaultMode && '(domyślny — nieedytowalny)'}
          </Typography>
          <List sx={{ bgcolor: 'background.paper', borderRadius: 2 }}>
            {activeMode.steps.map((step, i) => (
              <ListItem key={i} divider={i < activeMode.steps.length - 1}
                secondaryAction={!isDefaultMode ? (
                  <Box sx={{ display: 'flex' }}>
                    <IconButton size="small" onClick={() => moveStep(activeMode, i, -1)} disabled={i === 0}><ArrowUpwardIcon fontSize="small" /></IconButton>
                    <IconButton size="small" onClick={() => moveStep(activeMode, i, 1)} disabled={i === activeMode.steps.length - 1}><ArrowDownwardIcon fontSize="small" /></IconButton>
                    <IconButton size="small" onClick={() => {
                      const steps = activeMode.steps.filter((_, j) => j !== i);
                      store.deleteStudyMode(activeMode.id);
                      store.addStudyMode({ ...activeMode, steps });
                    }}><DeleteIcon fontSize="small" /></IconButton>
                  </Box>
                ) : undefined}>
                <ListItemText primary={`${i + 1}. ${stepSummary(step)}`} />
              </ListItem>
            ))}
          </List>
          {!isDefaultMode && (
            <Button size="small" startIcon={<AddIcon />} onClick={() => addStepToMode(activeMode)} sx={{ mt: 1 }}>
              Dodaj krok
            </Button>
          )}
        </Box>
      )}

      <Button variant="outlined" fullWidth onClick={() => setCreatingMode(true)} startIcon={<AddIcon />} sx={{ mb: 2 }}>
        Stwórz nowy tryb
      </Button>

      {creatingMode && (
        <Box sx={{ bgcolor: 'background.paper', p: 3, borderRadius: 2, mb: 3 }}>
          <TextField fullWidth label="Nazwa trybu" value={newModeName} onChange={e => setNewModeName(e.target.value)} sx={{ mb: 2 }} />
          <List>
            {customSteps.map((step, i) => (
              <ListItem key={i} secondaryAction={
                <Box sx={{ display: 'flex' }}>
                  <IconButton size="small" onClick={() => { const s = [...customSteps]; [s[i], s[Math.max(0, i - 1)]] = [s[Math.max(0, i - 1)], s[i]]; setCustomSteps(s); }} disabled={i === 0}><ArrowUpwardIcon fontSize="small" /></IconButton>
                  <IconButton size="small" onClick={() => { const s = [...customSteps]; const j = Math.min(s.length - 1, i + 1); [s[i], s[j]] = [s[j], s[i]]; setCustomSteps(s); }} disabled={i === customSteps.length - 1}><ArrowDownwardIcon fontSize="small" /></IconButton>
                  <IconButton size="small" onClick={() => setCustomSteps(s => s.filter((_, j) => j !== i))}><DeleteIcon fontSize="small" /></IconButton>
                </Box>
              }><ListItemText primary={`${i + 1}. ${stepSummary(step)}`} /></ListItem>
            ))}
          </List>
          <Box sx={{ display: 'flex', gap: 1, mt: 1 }}>
            <Button onClick={() => { setEditingModeId(null); setStepDialogOpen(true); }} startIcon={<AddIcon />}>Dodaj krok</Button>
            <Button variant="contained" onClick={saveCustomMode} disabled={!newModeName.trim() || customSteps.length === 0}>Zapisz tryb</Button>
          </Box>
        </Box>
      )}

      {/* Add step dialog */}
      <Dialog open={stepDialogOpen} onClose={() => setStepDialogOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Dodaj krok</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <TextField select fullWidth label="Typ" value={newStepType} onChange={e => setNewStepType(e.target.value)}>
              {Object.entries(STEP_LABELS).map(([k, v]) => <MenuItem key={k} value={k}>{v}</MenuItem>)}
            </TextField>
            {newStepType !== 'wait' && (
              <TextField fullWidth label="Indeks strony (od 0)" type="number" value={newPageIdx}
                onChange={e => setNewPageIdx(Number(e.target.value))} />
            )}
            {(newStepType === 'speak_page' || newStepType === 'dynamic_pause' || newStepType === 'wait') && (
              <TextField fullWidth label="Czas (ms)" type="number" value={newMs} onChange={e => setNewMs(Number(e.target.value))} />
            )}
            {newStepType === 'listen_and_branch' && (
              <TextField fullWidth label="Próg (%)" type="number" value={newThreshold}
                onChange={e => setNewThreshold(Number(e.target.value))} />
            )}
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setStepDialogOpen(false)}>Anuluj</Button>
          <Button variant="contained" onClick={confirmAddStep}>Dodaj</Button>
        </DialogActions>
      </Dialog>

      {/* Delete confirmation dialog */}
      <Dialog open={deleteDialogOpen} onClose={() => { setDeleteDialogOpen(false); setDeleteConfirmText(''); }}>
        <DialogTitle>Usuń zestaw</DialogTitle>
        <DialogContent>
          <Typography sx={{ mb: 2 }}>
            Aby potwierdzić usunięcie zestawu „{activeGroup.name}", wpisz <strong>DELETE</strong> poniżej:
          </Typography>
          <TextField fullWidth value={deleteConfirmText} onChange={e => setDeleteConfirmText(e.target.value)}
            placeholder="DELETE" autoFocus />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => { setDeleteDialogOpen(false); setDeleteConfirmText(''); }}>Anuluj</Button>
          <Button variant="contained" color="error" onClick={handleDelete}
            disabled={deleteConfirmText !== 'DELETE'}>Usuń</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

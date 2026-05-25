import { useState, useMemo, useEffect } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Paper from '@mui/material/Paper';
import AddIcon from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import { useNav } from '../App';
import { PageHeader } from '../components/PageHeader';
import { useI18n } from '../i18n';

const SEPARATORS: Record<string, string> = { tab: '\t', semicolon: ';', comma: ',', pipe: '|' };

const POPULAR_LANGS = [
  { code: 'pl-PL', label: 'Polski' }, { code: 'en-US', label: 'Angielski' },
  { code: 'es-ES', label: 'Hiszpański' }, { code: 'de-DE', label: 'Niemiecki' },
  { code: 'fr-FR', label: 'Francuski' }, { code: 'it-IT', label: 'Włoski' },
  { code: 'pt-PT', label: 'Portugalski' }, { code: 'ru-RU', label: 'Rosyjski' },
  { code: 'ja-JP', label: 'Japoński' }, { code: 'zh-CN', label: 'Chiński' },
];

function detectSeparator(text: string): string {
  const firstLine = text.split('\n').find(l => l.trim()) || '';
  const counts: Record<string, number> = {};
  for (const [key, sep] of Object.entries(SEPARATORS)) {
    counts[key] = firstLine.split(sep).length - 1;
  }
  let best = 'semicolon';
  for (const [key, count] of Object.entries(counts)) {
    if (count > (counts[best] || 0)) best = key;
  }
  return counts[best] > 0 ? best : 'semicolon';
}

function detectPageCount(text: string, sep: string): number {
  const lines = text.split('\n').filter(l => l.trim());
  if (lines.length === 0) return 2;
  const counts = lines.map(l => l.split(sep).length);
  const maxCols = Math.max(...counts);
  return Math.max(2, Math.min(5, maxCols));
}

export function ImportPage() {
  const { goBack, store } = useNav();
  const { t } = useI18n();
  const [name, setName] = useState('');
  const [sepKey, setSepKey] = useState('semicolon');
  const [pageCount, setPageCount] = useState(2);
  const [pageNames, setPageNames] = useState(['Phrase', 'Tłumaczenie', '', '', '']);
  const [pageLangs, setPageLangs] = useState(['en-US', 'pl-PL', '', '', '']);
  const [rawText, setRawText] = useState('');
  const [autoDetected, setAutoDetected] = useState(false);

  // Auto-detect separator and page count when text changes
  useEffect(() => {
    if (!rawText.trim()) { setAutoDetected(false); return; }
    if (autoDetected) return;
    const detectedSep = detectSeparator(rawText);
    setSepKey(detectedSep);
    const sep = SEPARATORS[detectedSep] || ';';
    const detectedCount = detectPageCount(rawText, sep);
    setPageCount(detectedCount);
    setAutoDetected(true);
  }, [rawText, autoDetected]);

  // Reset auto-detect when user manually changes text
  const handleTextChange = (text: string) => {
    setAutoDetected(false);
    setRawText(text);
  };

  const rows = useMemo(() => {
    const sep = SEPARATORS[sepKey] || ';';
    return rawText.split('\n').filter(l => l.trim()).map(line => {
      const parts = line.split(sep).map(s => s.trim());
      while (parts.length < pageCount) parts.push('');
      return parts.slice(0, pageCount);
    });
  }, [rawText, sepKey, pageCount]);

  const handleImport = () => {
    if (!name.trim()) return;
    const langs = pageLangs.slice(0, pageCount);
    const names = pageNames.slice(0, pageCount);
    const groupId = store.addGroup(name.trim(), langs, names);
    rows.forEach(row => store.addFlashcard(groupId, row));
    goBack();
  };

  const handleFile = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        setAutoDetected(false);
        if (file.name.endsWith('.json')) {
          try {
            const data = JSON.parse(reader.result);
            if (Array.isArray(data)) setRawText(data.map((r: string[]) => r.join(';')).join('\n'));
            else setRawText(reader.result);
          } catch { setRawText(reader.result); }
        } else {
          setRawText(reader.result);
        }
      }
    };
    reader.readAsText(file);
  };

  const updatePageName = (i: number, v: string) => { const n = [...pageNames]; n[i] = v; setPageNames(n); };
  const updatePageLang = (i: number, v: string) => { const n = [...pageLangs]; n[i] = v; setPageLangs(n); };

  return (
    <Box sx={{ p: 3, pb: 12, maxWidth: 800, mx: 'auto' }}>
      <PageHeader title={t('import.title')} onBack={goBack} />

      <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3, mb: 4 }}>
        <TextField fullWidth label={t('dashboard.name_label')} value={name} onChange={e => setName(e.target.value)} size="small" />

        {/* Paste area + file upload */}
        <TextField fullWidth multiline rows={5} label={t('import.csv_label')} value={rawText}
          onChange={e => handleTextChange(e.target.value)}
          placeholder={t('import.name_placeholder')} />

        <Button variant="outlined" component="label">
          {t('import.upload_btn')}
          <input type="file" hidden accept=".json,.txt,.csv" onChange={handleFile} />
        </Button>

        {/* Detected separator */}
        <TextField fullWidth select label={t('import.separator')} value={sepKey}
          onChange={e => setSepKey(e.target.value)} size="small">
          <MenuItem value="tab">{t('import.sep.tab')}</MenuItem>
          <MenuItem value="semicolon">{t('import.sep.semicolon')}</MenuItem>
          <MenuItem value="comma">{t('import.sep.comma')}</MenuItem>
          <MenuItem value="pipe">{t('import.sep.pipe')}</MenuItem>
        </TextField>

        {/* Page count with arrows */}
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
          <Typography variant="body2">{t('import.pages_count')}</Typography>
          <IconButton size="small" onClick={() => setPageCount(p => Math.max(2, p - 1))}
            disabled={pageCount <= 2}><RemoveIcon /></IconButton>
          <Typography variant="h6" sx={{ fontWeight: 700, minWidth: 32, textAlign: 'center' }}>{pageCount}</Typography>
          <IconButton size="small" onClick={() => setPageCount(p => Math.min(5, p + 1))}
            disabled={pageCount >= 5}><AddIcon /></IconButton>
        </Box>

        {Array.from({ length: pageCount }).map((_, i) => (
          <Box key={i} sx={{ display: 'flex', gap: 2 }}>
            <TextField label={t('import.page_name_label', { index: i + 1 })} value={pageNames[i]}
              onChange={e => updatePageName(i, e.target.value)} fullWidth size="small" />
            <TextField select label={t('import.lang_label')} value={pageLangs[i] || 'en-US'}
              onChange={e => updatePageLang(i, e.target.value)} sx={{ minWidth: 160 }} size="small">
              {POPULAR_LANGS.map(l => <MenuItem key={l.code} value={l.code}>{t('lang.' + l.code)}</MenuItem>)}
            </TextField>
          </Box>
        ))}
      </Box>

      {rows.length > 0 && (
        <>
          <Typography variant="h6" sx={{ mb: 2 }}>{t('import.preview')} ({rows.length})</Typography>
          <TableContainer component={Paper} sx={{ mb: 3, maxHeight: 300 }}>
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  {pageNames.slice(0, pageCount).map((n, i) => <TableCell key={i}>{n || t('import.page_label', { index: i + 1 })}</TableCell>)}
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.slice(0, 50).map((row, ri) => (
                  <TableRow key={ri}>{row.map((cell, ci) => <TableCell key={ci}>{cell}</TableCell>)}</TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </>
      )}

      <Button variant="contained" size="large" fullWidth onClick={handleImport}
        disabled={!name.trim() || rows.length === 0}>
        {t('import.btn', { count: rows.length })}
      </Button>
    </Box>
  );
}

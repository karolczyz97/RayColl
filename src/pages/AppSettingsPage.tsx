import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Slider from '@mui/material/Slider';
import Divider from '@mui/material/Divider';
import Stack from '@mui/material/Stack';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import FormControl from '@mui/material/FormControl';
import LightModeIcon from '@mui/icons-material/LightMode';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import SettingsBrightnessIcon from '@mui/icons-material/SettingsBrightness';
import { useNav } from '../App';
import { PageHeader } from '../components/PageHeader';
import { useI18n } from '../i18n';
import type { LanguageCode } from '../i18n';

export function AppSettingsPage() {
  const { goBack, store, themePref, setThemeMode, ttsRate, setTtsRate } = useNav();
  const { language, setLanguage, t } = useI18n();

  const handleExport = () => {
    const data = store.exportState();
    const blob = new Blob([data], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a'); a.href = url; a.download = 'fiszki-backup.json';
    a.click(); URL.revokeObjectURL(url);
  };

  const handleImport = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') store.importState(reader.result);
    };
    reader.readAsText(file);
  };

  return (
    <Box sx={{ p: 3, pb: 12, maxWidth: 600, mx: 'auto' }}>
      <PageHeader title={t('app_settings.title')} onBack={goBack} />

      <Stack spacing={3}>
        {/* Language Selection */}
        <Box>
          <Typography variant="h6" sx={{ mb: 2 }}>{t('app_settings.lang')}</Typography>
          <FormControl fullWidth size="small">
            <Select
              value={language}
              onChange={(e) => setLanguage(e.target.value as LanguageCode)}
            >
              <MenuItem value="pl">🇵🇱 Polski</MenuItem>
              <MenuItem value="en">🇬🇧 English</MenuItem>
              <MenuItem value="de">🇩🇪 Deutsch</MenuItem>
              <MenuItem value="es">🇪🇸 Español</MenuItem>
              <MenuItem value="it">🇮🇹 Italiano</MenuItem>
            </Select>
          </FormControl>
        </Box>

        <Divider />

        {/* Theme Preference */}
        <Box>
          <Typography variant="h6" sx={{ mb: 2 }}>{t('app_settings.theme')}</Typography>
          <ToggleButtonGroup
            value={themePref}
            exclusive
            onChange={(_, val) => { if (val) setThemeMode(val); }}
            fullWidth
            size="small"
          >
            <ToggleButton value="light">
              <LightModeIcon sx={{ mr: 1, fontSize: 20 }} />
              {t('app_settings.theme.light')}
            </ToggleButton>
            <ToggleButton value="system">
              <SettingsBrightnessIcon sx={{ mr: 1, fontSize: 20 }} />
              {t('app_settings.theme.system')}
            </ToggleButton>
            <ToggleButton value="dark">
              <DarkModeIcon sx={{ mr: 1, fontSize: 20 }} />
              {t('app_settings.theme.dark')}
            </ToggleButton>
          </ToggleButtonGroup>
        </Box>

        <Divider />

        {/* TTS speed */}
        <Box>
          <Typography variant="h6" sx={{ mb: 1 }}>{t('app_settings.tts_rate')}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            {ttsRate.toFixed(1)}x
          </Typography>
          <Slider value={ttsRate} onChange={(_, v) => setTtsRate(v as number)}
            min={0.5} max={2.0} step={0.1} valueLabelDisplay="auto"
            marks={[{ value: 0.5, label: '0.5x' }, { value: 1, label: '1x' }, { value: 2, label: '2x' }]} />
        </Box>

        <Divider />

        {/* Export / Import */}
        <Box>
          <Typography variant="h6" sx={{ mb: 2 }}>{t('app_settings.export_import')}</Typography>
          <Box sx={{ display: 'flex', gap: 2 }}>
            <Button variant="outlined" fullWidth onClick={handleExport}>
              {t('app_settings.export_btn')}
            </Button>
            <Button variant="outlined" fullWidth component="label">
              {t('app_settings.import_btn')}
              <input type="file" hidden accept=".json" onChange={handleImport} />
            </Button>
          </Box>
        </Box>

        <Divider />

        {/* Reset */}
        <Box>
          <Typography variant="h6" color="error" sx={{ mb: 1 }}>{t('app_settings.danger_zone')}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {t('app_settings.reset_confirm')}
          </Typography>
          <Button variant="contained" color="error" fullWidth onClick={() => {
            if (window.confirm(t('app_settings.reset_confirm'))) store.resetToDefault();
          }}>
            {t('app_settings.reset_btn')}
          </Button>
        </Box>
      </Stack>
    </Box>
  );
}

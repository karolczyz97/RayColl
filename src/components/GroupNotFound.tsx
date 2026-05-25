import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import { useI18n } from '../i18n';

interface GroupNotFoundProps {
  onBack: () => void;
}

export function GroupNotFound({ onBack }: GroupNotFoundProps) {
  const { t } = useI18n();
  return (
    <Box sx={{ p: 3, textAlign: 'center', maxWidth: 600, mx: 'auto', mt: 4 }}>
      <Typography variant="h5" sx={{ mb: 2, fontWeight: 700 }}>
        {t('study.group_not_found')}
      </Typography>
      <Button variant="contained" onClick={onBack}>
        {t('btn.back')}
      </Button>
    </Box>
  );
}

import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import Typography from '@mui/material/Typography';
import ArrowBackIcon from '@mui/icons-material/ArrowBack';

interface PageHeaderProps {
  title: string;
  onBack: () => void;
  action?: React.ReactNode;
}

export function PageHeader({ title, onBack, action }: PageHeaderProps) {
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 3 }}>
      <IconButton onClick={onBack} aria-label="back">
        <ArrowBackIcon />
      </IconButton>
      <Typography variant="h5" sx={{ fontWeight: 700, flexGrow: 1 }}>
        {title}
      </Typography>
      {action}
    </Box>
  );
}

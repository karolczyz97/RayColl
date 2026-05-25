import { createTheme } from '@mui/material/styles';
import type { ThemeOptions } from '@mui/material/styles';

// Definicja niestandardowych właściwości w motywie (np. dodatkowe skale zaokrągleń dla M3 Expressive)
declare module '@mui/material/styles' {
  interface Theme {
    shapes: {
      extraSmall: string;
      small: string;
      medium: string;
      large: string;
      extraLarge: string;
      full: string;
    };
  }
  interface ThemeOptions {
    shapes?: {
      extraSmall?: string;
      small?: string;
      medium?: string;
      large?: string;
      extraLarge?: string;
      full?: string;
    };
  }
  interface Palette {
    tertiary: Palette['primary'];
    surfaceVariant: string;
    outline: string;
  }
  interface PaletteOptions {
    tertiary?: PaletteOptions['primary'];
    surfaceVariant?: string;
    outline?: string;
  }
}

// Baza kształtów Material 3 Expressive
const m3Shapes = {
  extraSmall: '4px',
  small: '8px',
  medium: '12px',
  large: '16px',
  extraLarge: '28px',
  full: '9999px',
};

// Podstawowa konfiguracja typografii M3 (rekomendowane czcionki: Outfit, Inter)
const m3Typography = {
  fontFamily: '"Outfit", "Inter", "Roboto", "Helvetica", "Arial", sans-serif',
  h1: { fontWeight: 400, fontSize: '3.5rem', lineHeight: 1.15 },
  h2: { fontWeight: 400, fontSize: '2.75rem', lineHeight: 1.2 },
  h3: { fontWeight: 400, fontSize: '2.25rem', lineHeight: 1.25 },
  h4: { fontWeight: 400, fontSize: '1.75rem', lineHeight: 1.3 },
  h5: { fontWeight: 700, fontSize: '1.4rem', lineHeight: 1.3 }, // Page title
  h6: { fontWeight: 600, fontSize: '1.15rem', lineHeight: 1.3 }, // Subheading
  subtitle1: { fontWeight: 500, fontSize: '1rem', lineHeight: 1.4 },
  subtitle2: { fontWeight: 500, fontSize: '0.875rem', lineHeight: 1.4 },
  body1: { fontWeight: 400, fontSize: '1rem', lineHeight: 1.5 },
  body2: { fontWeight: 400, fontSize: '0.875rem', lineHeight: 1.5 },
  button: { textTransform: 'none' as const, fontWeight: 500 },
};

/**
 * Tworzy motyw MUI zgodny z Material 3 Expressive.
 * @param mode Tryb jasny/ciemny ('light' | 'dark')
 * @param customColors Opcjonalne kolory dynamiczne (np. wyciągnięte z systemu Android/Material You)
 */
export function createM3Theme(mode: 'light' | 'dark', customColors?: { primary?: string; secondary?: string }) {
  const isLight = mode === 'light';

  const themeOptions: ThemeOptions = {
    palette: {
      mode,
      primary: {
        main: customColors?.primary || (isLight ? '#6750A4' : '#D0BCFF'), // Domyślny fiolet M3
        contrastText: isLight ? '#FFFFFF' : '#381E72',
      },
      secondary: {
        main: customColors?.secondary || (isLight ? '#625B71' : '#CCC2DC'),
        contrastText: isLight ? '#FFFFFF' : '#332D41',
      },
      tertiary: {
        main: isLight ? '#7D5260' : '#EFB8C8',
        contrastText: isLight ? '#FFFFFF' : '#492532',
      },
      background: {
        default: isLight ? '#FEF7FF' : '#141218',
        paper: isLight ? '#FFFFFF' : '#1D1B20',
      },
      error: {
        main: isLight ? '#B3261E' : '#F2B8B5',
      },
      surfaceVariant: isLight ? '#E7E0EC' : '#49454F',
      outline: isLight ? '#79747E' : '#938F99',
    },
    typography: m3Typography,
    shapes: m3Shapes,
    components: {
      MuiButton: {
        styleOverrides: {
          root: {
            borderRadius: m3Shapes.full, // Przyciski w M3 są w pełni zaokrąglone (pill-shaped)
            padding: '10px 24px',
            transition: 'all 0.2s cubic-bezier(0.2, 0, 0, 1)', // Płynne animacje M3
            '&:hover': {
              transform: 'scale(1.02)', // Delikatne powiększenie w stylu Expressive
            },
          },
        },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            borderRadius: m3Shapes.large, // Zaokrąglone karty
            backgroundImage: 'none',
            boxShadow: '0px 1px 3px 0px rgba(0, 0, 0, 0.12), 0px 1px 2px 0px rgba(0, 0, 0, 0.24)',
            transition: 'transform 0.2s cubic-bezier(0.2, 0, 0, 1), box-shadow 0.2s',
            '&:hover': {
              transform: 'translateY(-2px) scale(1.01)',
              boxShadow: '0px 4px 8px 3px rgba(0, 0, 0, 0.15), 0px 1px 3px 0px rgba(0, 0, 0, 0.3)',
            },
          },
        },
      },
      MuiFab: {
        styleOverrides: {
          root: {
            borderRadius: m3Shapes.large, // FAB w M3 Expressive są zaokrąglonymi kwadratami
            transition: 'all 0.2s cubic-bezier(0.2, 0, 0, 1)',
            '&:hover': {
              transform: 'scale(1.05) rotate(5deg)', // Dynamiczny hover Expressive
            },
          },
        },
      },
      MuiDialog: {
        styleOverrides: {
          paper: {
            borderRadius: m3Shapes.extraLarge, // Zaokrąglony dialog (28px)
            padding: '8px',
          },
        },
      },
      MuiChip: {
        styleOverrides: {
          root: {
            borderRadius: m3Shapes.small, // M3 Chips są lekko zaokrąglone (8px)
            fontWeight: 500,
          },
        },
      },
      MuiTextField: {
        styleOverrides: {
          root: {
            '& .MuiOutlinedInput-root': {
              borderRadius: m3Shapes.medium, // Umiarkowanie zaokrąglone textfields (12px)
            },
          },
        },
      },
    },
  };

  return createTheme(themeOptions);
}

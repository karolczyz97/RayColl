import { createContext, useContext, useState, useCallback, useMemo, useEffect, Component, type ReactNode, type ErrorInfo } from 'react';
import { HashRouter, BrowserRouter, useNavigate, useLocation } from 'react-router-dom';
import { ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import Box from '@mui/material/Box';
import { AnimatePresence, motion } from 'framer-motion';
import { createM3Theme } from './theme';
import { useFlashcardStore } from './hooks/useFlashcardStore';
import type { FlashcardStore } from './hooks/useFlashcardStore';
import { DashboardPage } from './pages/DashboardPage';
import { StudyPage } from './pages/StudyPage';
import { BrowsePage } from './pages/BrowsePage';
import { ImportPage } from './pages/ImportPage';
import { SettingsPage } from './pages/SettingsPage';
import { StatsPage } from './pages/StatsPage';
import { AppSettingsPage } from './pages/AppSettingsPage';

export type PageId = 'dashboard' | 'study' | 'browse' | 'import' | 'settings' | 'stats' | 'app-settings';
export interface NavState { page: PageId; params: Record<string, string>; }

interface NavContextType {
  nav: NavState;
  navigate: (page: PageId, params?: Record<string, string>) => void;
  goBack: () => void;
  store: FlashcardStore;
  themePref: 'light' | 'dark' | 'system';
  themeMode: 'light' | 'dark';
  setThemeMode: (mode: 'light' | 'dark' | 'system') => void;
  ttsRate: number;
  setTtsRate: (r: number) => void;
}

export const NavContext = createContext<NavContextType>(null!);
export const useNav = () => useContext(NavContext);

function AppContent() {
  const navigateRouter = useNavigate();
  const location = useLocation();

  const [themePref, setThemePref] = useState<'light' | 'dark' | 'system'>('system');
  const [ttsRate, setTtsRate] = useState(1.0);
  const store = useFlashcardStore();

  const [systemDark, setSystemDark] = useState(
    typeof window !== 'undefined' ? window.matchMedia('(prefers-color-scheme: dark)').matches : false
  );
  useEffect(() => {
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const handler = (e: MediaQueryListEvent) => setSystemDark(e.matches);
    mq.addEventListener('change', handler);
    return () => mq.removeEventListener('change', handler);
  }, []);

  const themeMode: 'light' | 'dark' = themePref === 'system' ? (systemDark ? 'dark' : 'light') : themePref;
  const theme = useMemo(() => createM3Theme(themeMode), [themeMode]);

  const pageId = useMemo((): PageId => {
    const path = location.pathname;
    if (path.startsWith('/study/')) return 'study';
    if (path.startsWith('/browse/')) return 'browse';
    if (path.startsWith('/settings/')) return 'settings';
    if (path === '/import') return 'import';
    if (path === '/stats') return 'stats';
    if (path === '/app-settings') return 'app-settings';
    return 'dashboard';
  }, [location.pathname]);

  const nav = useMemo((): NavState => {
    const path = location.pathname;
    const parts = path.split('/');
    const parsedParams: Record<string, string> = {};
    if (path.startsWith('/study/') || path.startsWith('/browse/') || path.startsWith('/settings/')) {
      if (parts[2]) {
        parsedParams.groupId = decodeURIComponent(parts[2]);
      }
    }
    return {
      page: pageId,
      params: parsedParams,
    };
  }, [pageId, location.pathname]);

  const navigate = useCallback((page: PageId, navigateParams: Record<string, string> = {}) => {
    let path = '/';
    switch (page) {
      case 'dashboard': path = '/'; break;
      case 'study': path = `/study/${navigateParams.groupId || ''}`; break;
      case 'browse': path = `/browse/${navigateParams.groupId || ''}`; break;
      case 'settings': path = `/settings/${navigateParams.groupId || ''}`; break;
      case 'import': path = '/import'; break;
      case 'stats': path = '/stats'; break;
      case 'app-settings': path = '/app-settings'; break;
    }
    navigateRouter(path);
  }, [navigateRouter]);

  const goBack = useCallback(() => {
    navigateRouter('/');
  }, [navigateRouter]);

  const ctx = useMemo<NavContextType>(() => ({
    nav, navigate, goBack, store, themePref, themeMode,
    setThemeMode: setThemePref, ttsRate, setTtsRate,
  }), [nav, navigate, goBack, store, themePref, themeMode, ttsRate]);

  const renderPage = () => {
    switch (pageId) {
      case 'dashboard': return <DashboardPage />;
      case 'study': return <StudyPage />;
      case 'browse': return <BrowsePage />;
      case 'settings': return <SettingsPage />;
      case 'import': return <ImportPage />;
      case 'stats': return <StatsPage />;
      case 'app-settings': return <AppSettingsPage />;
      default: return <DashboardPage />;
    }
  };

  return (
    <NavContext.Provider value={ctx}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <Box sx={{ minHeight: '100vh', bgcolor: 'background.default' }}>
          <AnimatePresence mode="wait">
            <motion.div
              key={pageId + (nav.params.groupId || '')}
              initial={{ opacity: 0, x: 30 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -30 }}
              transition={{ duration: 0.25, ease: [0.2, 0, 0, 1] }}
            >
              {renderPage()}
            </motion.div>
          </AnimatePresence>
        </Box>
      </ThemeProvider>
    </NavContext.Provider>
  );
}

class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { hasError: false, error: null };

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("ErrorBoundary caught an error:", error, errorInfo);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div style={{
          padding: '24px',
          maxWidth: '600px',
          margin: '40px auto',
          backgroundColor: '#ffebee',
          color: '#c62828',
          borderRadius: '8px',
          fontFamily: 'sans-serif',
          boxShadow: '0 4px 12px rgba(0,0,0,0.1)'
        }}>
          <h2 style={{ margin: '0 0 12px 0', fontSize: '24px' }}>Coś poszło nie tak... 😢</h2>
          <p style={{ margin: '0 0 16px 0', fontSize: '16px' }}>
            Aplikacja napotkała błąd podczas wyświetlania strony.
          </p>
          <pre style={{
            padding: '12px',
            backgroundColor: '#ffcdd2',
            borderRadius: '4px',
            overflowX: 'auto',
            fontSize: '14px',
            fontFamily: 'monospace',
            margin: '0 0 20px 0'
          }}>
            {this.state.error?.stack || this.state.error?.toString()}
          </pre>
          <button
            onClick={() => window.location.href = '/'}
            style={{
              padding: '10px 20px',
              backgroundColor: '#c62828',
              color: '#ffffff',
              border: 'none',
              borderRadius: '4px',
              cursor: 'pointer',
              fontWeight: 'bold',
              fontSize: '14px'
            }}
          >
            Powrót do strony głównej
          </button>
        </div>
      );
    }
    return this.props.children;
  }
}

interface ErrorBoundaryProps {
  children: ReactNode;
}
interface ErrorBoundaryState {
  hasError: boolean;
  error: Error | null;
}

// Detect if running in a static/hybrid client (Capacitor or Tauri)
const useHashRouter = typeof window !== 'undefined' && (
  window.location.protocol === 'file:' ||
  window.location.pathname.includes('index.html') ||
  (window as any).__TAURI__ ||
  (window as any).Capacitor
);

export default function App() {
  const Router = useHashRouter ? HashRouter : BrowserRouter;
  return (
    <ErrorBoundary>
      <Router>
        <AppContent />
      </Router>
    </ErrorBoundary>
  );
}

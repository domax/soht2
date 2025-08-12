import React from 'react';
import { createTheme, CssBaseline, ThemeProvider } from '@mui/material';

export type ThemeMode = 'light' | 'dark';

const STORAGE_KEY = 'soht2-ui.theme';

function getInitialMode(): ThemeMode {
  const saved = (localStorage.getItem(STORAGE_KEY) || '').toLowerCase();
  if (saved === 'light' || saved === 'dark') return saved;
  const prefersDark = window.matchMedia?.('(prefers-color-scheme: dark)').matches;
  return prefersDark ? 'dark' : 'light';
}

// eslint-disable-next-line react-refresh/only-export-components
export const ThemeModeContext = React.createContext<{ mode: ThemeMode; toggle: () => void }>({
  mode: 'light',
  toggle: () => {},
});

export function ThemeModeProvider({ children }: Readonly<{ children: React.ReactNode }>) {
  const [mode, setMode] = React.useState<ThemeMode>(getInitialMode);

  const toggle = React.useCallback(() => {
    setMode(prev => {
      const next: ThemeMode = prev === 'light' ? 'dark' : 'light';
      localStorage.setItem(STORAGE_KEY, next);
      return next;
    });
  }, []);

  const theme = React.useMemo(() => createTheme({ palette: { mode } }), [mode]);

  const ctx = React.useMemo(() => ({ mode, toggle }), [mode, toggle]);

  return (
    <ThemeModeContext.Provider value={ctx}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        {children}
      </ThemeProvider>
    </ThemeModeContext.Provider>
  );
}

// eslint-disable-next-line react-refresh/only-export-components
export function useThemeMode() {
  return React.useContext(ThemeModeContext);
}

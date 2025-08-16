/* SOHT2 © Licensed under MIT 2025. */
import { type ReactNode, createContext, useContext, useState, useMemo, useCallback } from 'react';
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
export const ThemeModeContext = createContext<{ mode: ThemeMode; toggle: () => void }>({
  mode: 'light',
  toggle: () => {},
});

export function ThemeModeProvider({ children }: Readonly<{ children: ReactNode }>) {
  const [mode, setMode] = useState<ThemeMode>(getInitialMode);

  const toggle = useCallback(() => {
    setMode(prev => {
      const next: ThemeMode = prev === 'light' ? 'dark' : 'light';
      localStorage.setItem(STORAGE_KEY, next);
      return next;
    });
  }, []);

  const theme = useMemo(() => createTheme({ palette: { mode } }), [mode]);

  const ctx = useMemo(() => ({ mode, toggle }), [mode, toggle]);

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
  return useContext(ThemeModeContext);
}

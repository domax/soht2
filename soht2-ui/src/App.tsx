/* SOHT2 © Licensed under MIT 2025. */
import './App.css';
import { lazy, Suspense, useCallback, useMemo, useState } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { ThemeModeProvider } from './theme';
import type { Soht2User } from './api/soht2Api';
import CircularProgress from '@mui/material/CircularProgress';

const LazyLoginPage = lazy(() => import('./pages/LoginPage'));
const LazyAdminPage = lazy(() => import('./pages/AdminPage'));
const LazyUserPage = lazy(() => import('./pages/UserPage'));

export type WindowProps = typeof window & { __CONTEXT_PATH__: string };

export default function App() {
  const [user, setUser] = useState<Soht2User | null>(null);
  const isAdmin = useMemo(() => (user?.role || '').toUpperCase() === 'ADMIN', [user]);

  const handleLogin = useCallback((u: Soht2User) => setUser(u), []);

  return (
    <ThemeModeProvider>
      <BrowserRouter basename={(window as WindowProps).__CONTEXT_PATH__ || '/'}>
        <Routes>
          <Route
            path="/"
            element={
              user ? (
                <Navigate to={isAdmin ? '/admin' : '/user'} replace />
              ) : (
                <Navigate to="/login" replace />
              )
            }
          />
          <Route
            path="/login"
            element={
              <Suspense fallback={<CircularProgress />}>
                <LazyLoginPage onLogin={handleLogin} />
              </Suspense>
            }
          />
          <Route
            path="/admin"
            element={
              <Suspense fallback={<CircularProgress />}>
                <LazyAdminPage user={user} />
              </Suspense>
            }
          />
          <Route
            path="/user"
            element={
              <Suspense fallback={<CircularProgress />}>
                <LazyUserPage user={user} />
              </Suspense>
            }
          />
        </Routes>
      </BrowserRouter>
    </ThemeModeProvider>
  );
}

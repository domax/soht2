/* SOHT2 © Licensed under MIT 2025. */
import './App.css';
import { useState, useMemo, Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
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
                <LazyLoginPage onLogin={u => setUser(u)} />
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

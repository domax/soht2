import './App.css';
import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import type { Soht2User } from './api/soht2Api';
import { LoginPage } from './pages/LoginPage';
import { AdminPage } from './pages/AdminPage';
import { UserPage } from './pages/UserPage';
import { ThemeModeProvider } from './theme';

function App() {
  const [user, setUser] = React.useState<Soht2User | null>(null);
  const isAdmin = React.useMemo(() => (user?.role || '').toUpperCase() === 'ADMIN', [user]);

  return (
    <ThemeModeProvider>
      <BrowserRouter>
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
          <Route path="/login" element={<LoginPage onLogin={u => setUser(u)} />} />
          <Route path="/admin" element={<AdminPage />} />
          <Route path="/user" element={<UserPage />} />
        </Routes>
      </BrowserRouter>
    </ThemeModeProvider>
  );
}

export default App;

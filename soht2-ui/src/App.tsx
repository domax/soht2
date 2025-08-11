import soht2Logo from '/soht2_logo.png'; // NOSONAR typescript:S6859
import './App.css';
import React from 'react';
import { BrowserRouter, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import Container from '@mui/material/Container';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Snackbar from '@mui/material/Snackbar';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';
import { UserApi, httpClient, type Soht2User } from './api/soht2Api';

function Layout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <>
      <AppBar position="static">
        <Toolbar>
          <img src={soht2Logo} alt="Logo" style={{ height: 40, marginRight: 16 }} />
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            SOHT2 Server
          </Typography>
        </Toolbar>
      </AppBar>
      <Container sx={{ marginTop: 4 }}>{children}</Container>
    </>
  );
}

function LoginPage({ onLogin }: Readonly<{ onLogin: (user: Soht2User) => void }>) {
  const navigate = useNavigate();
  const [username, setUsername] = React.useState('');
  const [password, setPassword] = React.useState('');
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const handleSubmit = async () => {
    setLoading(true);
    setError(null);
    try {
      httpClient.setBasicAuth(username, password);
      const self = await UserApi.getSelf();
      onLogin(self); // memoize user's info in parent
      // Role-based navigation
      if ((self.role || '').toUpperCase() === 'ADMIN') {
        navigate('/admin', { replace: true });
      } else {
        navigate('/user', { replace: true });
      }
    } catch (e: unknown) {
      // clear auth on failure
      httpClient.clearAuth();
      const msg = e instanceof Error ? e.message : 'Invalid credentials';
      setError(msg);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Layout>
      <Stack spacing={2} sx={{ maxWidth: 600 }}>
        <TextField
          label="Username"
          variant="outlined"
          fullWidth
          margin="normal"
          value={username}
          onChange={e => setUsername(e.target.value)}
          autoComplete="username"
        />
        <TextField
          label="Password"
          type="password"
          variant="outlined"
          fullWidth
          margin="normal"
          value={password}
          onChange={e => setPassword(e.target.value)}
          autoComplete="current-password"
          onKeyDown={e => {
            if (e.key === 'Enter' && !loading) handleSubmit();
          }}
        />
        <Button variant="contained" color="primary" disabled={loading} onClick={handleSubmit}>
          {loading ? (
            <>
              <CircularProgress size={20} sx={{ mr: 1 }} /> Logging in...
            </>
          ) : (
            'Login'
          )}
        </Button>
      </Stack>
      <Snackbar
        open={!!error}
        autoHideDuration={6000}
        onClose={() => setError(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}>
        <Alert
          onClose={() => setError(null)}
          severity="error"
          variant="filled"
          sx={{ width: '100%' }}>
          {error}
        </Alert>
      </Snackbar>
    </Layout>
  );
}

function AdminPage() {
  return <Layout>{/* Admin page content */}</Layout>;
}

function UserPage() {
  return <Layout>{/* User page content */}</Layout>;
}

function App() {
  const [user, setUser] = React.useState<Soht2User | null>(null);
  const isAdmin = React.useMemo(() => (user?.role || '').toUpperCase() === 'ADMIN', [user]);

  return (
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
  );
}

export default App;

/* SOHT2 © Licensed under MIT 2025. */
import { useCallback, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import CircularProgress from '@mui/material/CircularProgress';
import { type ApiError, httpClient, type Soht2User, UserApi } from '../api/soht2Api';
import { dispatchAppErrorEvent } from '../api/appEvents';
import Layout from '../components/Layout';
import PasswordField from '../controls/PasswordField';

export default function LoginPage({ onLogin }: Readonly<{ onLogin: (user: Soht2User) => void }>) {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [empty, setEmpty] = useState(true);
  const [loading, setLoading] = useState(false);

  const handleSubmit = useCallback(async () => {
    setLoading(true);
    try {
      httpClient.setBasicAuth(username, password);
      const user = await UserApi.getSelf();
      onLogin(user);
      navigate((user.role ?? '').toUpperCase() === 'ADMIN' ? '/admin' : '/user', { replace: true });
    } catch (e) {
      httpClient.clearAuth();
      dispatchAppErrorEvent(e as ApiError);
    } finally {
      setLoading(false);
    }
  }, [onLogin, username, password, navigate]);

  return (
    <Layout>
      <Stack spacing={2} sx={{ maxWidth: 420, margin: 'auto', marginTop: 6 }}>
        <TextField
          label="Username"
          value={username}
          variant="outlined"
          margin="normal"
          autoComplete="username"
          fullWidth
          required
          onChange={e => {
            const u = e.target.value;
            setUsername(u);
            setEmpty(!u || !password);
          }}
        />
        <PasswordField
          password={password}
          fullWidth
          variant="outlined"
          margin="normal"
          autoComplete="current-password"
          onChange={p => {
            setPassword(p);
            setEmpty(!username || !p);
          }}
          onEnter={() => {
            if (!empty && !loading) void handleSubmit();
          }}
        />
        <Button
          variant="contained"
          color="primary"
          disabled={empty || loading}
          onClick={handleSubmit}>
          {loading ? (
            <>
              <CircularProgress size={20} sx={{ mr: 1 }} /> Logging in...
            </>
          ) : (
            'Login'
          )}
        </Button>
      </Stack>
    </Layout>
  );
}

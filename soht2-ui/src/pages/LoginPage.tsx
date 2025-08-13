import React from 'react';
import { useNavigate } from 'react-router-dom';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import ErrorAlert from '../components/ErrorAlert';
import CircularProgress from '@mui/material/CircularProgress';
import { UserApi, httpClient, type Soht2User, ApiError } from '../api/soht2Api';
import { Layout } from '../components/Layout';

export function LoginPage({ onLogin }: Readonly<{ onLogin: (user: Soht2User) => void }>) {
  const navigate = useNavigate();
  const [username, setUsername] = React.useState('');
  const [password, setPassword] = React.useState('');
  const [empty, setEmpty] = React.useState(true);
  const [loading, setLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  const handleSubmit = async () => {
    setLoading(true);
    setError(null);
    try {
      httpClient.setBasicAuth(username, password);
      const self = await UserApi.getSelf();
      onLogin(self);
      navigate((self.role || '').toUpperCase() === 'ADMIN' ? '/admin' : '/user', { replace: true });
    } catch (e: unknown) {
      httpClient.clearAuth();
      const apiError: ApiError = e as ApiError;
      setError(apiError.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <Layout>
      <Stack spacing={2} sx={{ maxWidth: 420, margin: 'auto', marginTop: 6 }}>
        <TextField
          label="Username"
          variant="outlined"
          fullWidth
          margin="normal"
          value={username}
          required={true}
          onChange={e => {
            setUsername(e.target.value);
            setEmpty(!e.target.value || !password);
          }}
          autoComplete="username"
        />
        <TextField
          label="Password"
          type="password"
          variant="outlined"
          fullWidth
          margin="normal"
          value={password}
          required={true}
          onChange={e => {
            setPassword(e.target.value);
            setEmpty(!username || !e.target.value);
          }}
          autoComplete="current-password"
          onKeyDown={e => {
            if (e.key === 'Enter' && !empty && !loading) handleSubmit();
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

      <ErrorAlert message={error} onClose={() => setError(null)} />
    </Layout>
  );
}

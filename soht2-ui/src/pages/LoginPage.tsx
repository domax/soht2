import React from 'react';
import { useNavigate } from 'react-router-dom';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import { APP_ERROR_EVENT } from '../components/ErrorAlert';
import CircularProgress from '@mui/material/CircularProgress';
import { UserApi, httpClient, type Soht2User, type ApiError } from '../api/soht2Api';
import { Layout } from '../components/Layout';

export default function LoginPage({ onLogin }: Readonly<{ onLogin: (user: Soht2User) => void }>) {
  const navigate = useNavigate();
  const [username, setUsername] = React.useState('');
  const [password, setPassword] = React.useState('');
  const [empty, setEmpty] = React.useState(true);
  const [loading, setLoading] = React.useState(false);

  const handleSubmit = async () => {
    setLoading(true);
    try {
      httpClient.setBasicAuth(username, password);
      const self = await UserApi.getSelf();
      onLogin(self);
      navigate((self.role || '').toUpperCase() === 'ADMIN' ? '/admin' : '/user', { replace: true });
    } catch (e) {
      httpClient.clearAuth();
      window.dispatchEvent(new CustomEvent<ApiError>(APP_ERROR_EVENT, { detail: e as ApiError }));
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
    </Layout>
  );
}

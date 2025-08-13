/* SOHT2 © Licensed under MIT 2025. */
import React from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import { APP_ERROR_EVENT } from './ErrorAlert';
import CircularProgress from '@mui/material/CircularProgress';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import InputAdornment from '@mui/material/InputAdornment';
import IconButton from '@mui/material/IconButton';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import { type ApiError, UserApi, type UserRole } from '../api/soht2Api';
import AllowedTargets from './AllowedTargets.tsx';

type NewUserDialogProps = Readonly<{ open: boolean; onClose: () => void }>;
type NewUserForm = { username: string; password: string; role: UserRole; allowedTargets: string[] };

export default function NewUserDialog({ open, onClose }: NewUserDialogProps) {
  const [form, setForm] = React.useState<NewUserForm>({
    username: '',
    password: '',
    role: 'USER',
    allowedTargets: ['*:*'],
  });
  const [showPassword, setShowPassword] = React.useState(false);
  const [submitting, setSubmitting] = React.useState(false);

  const emptyRequired = !form.username || !form.password;

  const resetAndClose = () => {
    if (submitting) return;
    setForm({ username: '', password: '', role: 'USER', allowedTargets: [] });
    setShowPassword(false);
    onClose();
  };

  const handleSubmit = async () => {
    if (emptyRequired) return;
    setSubmitting(true);
    try {
      await UserApi.createUser({
        username: form.username,
        password: form.password,
        role: form.role,
        allowedTargets: form.allowedTargets,
      });
      // Notify listeners (e.g., UsersTable) that users' list has changed
      window.dispatchEvent(
        new CustomEvent('users:changed', { detail: { action: 'create', username: form.username } })
      );
      resetAndClose();
    } catch (e) {
      window.dispatchEvent(new CustomEvent<ApiError>(APP_ERROR_EVENT, { detail: e as ApiError }));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={resetAndClose} fullWidth maxWidth="sm">
      <DialogTitle>New User</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label="Username"
            value={form.username}
            required
            onChange={e => setForm(prev => ({ ...prev, username: e.target.value }))}
            autoFocus
            autoComplete="username"
          />

          <TextField
            label="Password"
            value={form.password}
            required
            type={showPassword ? 'text' : 'password'}
            onChange={e => setForm(prev => ({ ...prev, password: e.target.value }))}
            autoComplete="new-password"
            slotProps={{
              input: {
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton
                      aria-label={showPassword ? 'Hide password' : 'Show password'}
                      onClick={() => setShowPassword(p => !p)}
                      edge="end"
                      size="small">
                      {showPassword ? <VisibilityOff /> : <Visibility />}
                    </IconButton>
                  </InputAdornment>
                ),
              },
            }}
          />

          <FormControl fullWidth>
            <InputLabel id="role-label">Role</InputLabel>
            <Select
              labelId="role-label"
              label="Role"
              value={form.role}
              onChange={e =>
                setForm(prev => ({ ...prev, role: e.target.value as 'USER' | 'ADMIN' }))
              }>
              <MenuItem value="USER">USER</MenuItem>
              <MenuItem value="ADMIN">ADMIN</MenuItem>
            </Select>
          </FormControl>

          <AllowedTargets
            targets={form.allowedTargets}
            setTargets={targets => setForm(prev => ({ ...prev, allowedTargets: targets }))}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={resetAndClose} disabled={submitting} color="inherit">
          Cancel
        </Button>
        <Button onClick={handleSubmit} disabled={emptyRequired || submitting} variant="contained">
          {submitting ? (
            <>
              <CircularProgress size={20} sx={{ mr: 1 }} /> Creating...
            </>
          ) : (
            'Create User'
          )}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

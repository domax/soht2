import React from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import ErrorAlert from './ErrorAlert';
import CircularProgress from '@mui/material/CircularProgress';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import Chip from '@mui/material/Chip';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import InputAdornment from '@mui/material/InputAdornment';
import IconButton from '@mui/material/IconButton';
import Visibility from '@mui/icons-material/Visibility';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import { type ApiError, type Soht2User, UserApi } from '../api/soht2Api';

const TARGET_REGEX = /^[a-z0-9.*-]+:[0-9*]+$/;

export type EditUserDialogProps = { open: boolean; user: Soht2User | null; onClose: () => void };

type UserRole = 'USER' | 'ADMIN';

export default function EditUserDialog({ open, user, onClose }: Readonly<EditUserDialogProps>) {
  const [password, setPassword] = React.useState('');
  const [role, setRole] = React.useState<UserRole>((user?.role || 'USER') as UserRole);
  const [allowedTargets, setAllowedTargets] = React.useState<string[]>(user?.allowedTargets ?? []);

  const [initialRole] = React.useState<UserRole>((user?.role || 'USER') as UserRole);
  const [initialTargets] = React.useState<string[]>(user?.allowedTargets ?? []);

  const [targetInput, setTargetInput] = React.useState('');
  const [targetError, setTargetError] = React.useState<string | null>(null);
  const [showPassword, setShowPassword] = React.useState(false);
  const [submitting, setSubmitting] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);

  React.useEffect(() => {
    // Reset when user changes/open toggles
    if (open) {
      setPassword('');
      setShowPassword(false);
      setTargetInput('');
      setTargetError(null);
      setError(null);
      setRole((user?.role || 'USER') as UserRole);
      setAllowedTargets(user?.allowedTargets ?? []);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, user?.username]);

  const addTarget = () => {
    const value = targetInput.trim();
    if (!value) return;
    if (!TARGET_REGEX.test(value)) {
      setTargetError("Invalid format. Expected something like 'host:123' or '*.host:*");
      return;
    }
    if (allowedTargets.includes(value)) {
      setTargetError('Target already added');
      return;
    }
    setAllowedTargets(prev => [...prev, value]);
    setTargetInput('');
    setTargetError(null);
  };

  const removeTarget = (t: string) => {
    setAllowedTargets(prev => prev.filter(x => x !== t));
  };

  const handleSubmit = async () => {
    if (!user) return;
    setSubmitting(true);
    setError(null);
    try {
      const params: {
        password?: string | null;
        role?: string | null;
        allowedTargets?: string[] | null;
      } = {};
      if (password) params.password = password; // optional; only send if non-empty
      if (role && role !== initialRole) params.role = role;
      // Only send allowedTargets if changed. Note: empty array would be ignored by API implementation
      const changedTargets = JSON.stringify(allowedTargets) !== JSON.stringify(initialTargets);
      if (changedTargets) params.allowedTargets = allowedTargets;

      await UserApi.updateUser(user.username, params);
      window.dispatchEvent(
        new CustomEvent('users:changed', { detail: { action: 'update', username: user.username } })
      );
      onClose();
    } catch (e: unknown) {
      const apiError = e as ApiError;
      setError(apiError.errors?.[0] ? apiError.errors[0].defaultMessage : apiError.message);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <Dialog
        open={open}
        onClose={() => (!submitting ? onClose() : undefined)}
        fullWidth
        maxWidth="sm">
        <DialogTitle>Edit User</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Username"
              value={user?.username}
              slotProps={{ input: { readOnly: true } }}
            />

            <TextField
              label="Password"
              value={password}
              type={showPassword ? 'text' : 'password'}
              onChange={e => setPassword(e.target.value)}
              autoComplete="new-password"
              helperText="Leave empty to keep unchanged"
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
              <InputLabel id="role-label-edit">Role</InputLabel>
              <Select
                labelId="role-label-edit"
                label="Role"
                value={role || 'USER'}
                onChange={e => setRole(e.target.value as UserRole)}>
                <MenuItem value="USER">USER</MenuItem>
                <MenuItem value="ADMIN">ADMIN</MenuItem>
              </Select>
            </FormControl>

            <Box>
              <TextField
                label="Allowed Target"
                placeholder="e.g. host:123 or *.host:*"
                value={targetInput}
                onChange={e => {
                  setTargetInput(e.target.value);
                  if (targetError) setTargetError(null);
                }}
                onKeyDown={e => {
                  if (e.key === 'Enter') {
                    e.preventDefault();
                    addTarget();
                  }
                }}
                error={!!targetError}
                helperText={targetError || 'Press Enter to add target'}
                fullWidth
              />
              {allowedTargets.length > 0 && (
                <Box sx={{ mt: 1, display: 'flex', flexWrap: 'wrap', gap: 1 }}>
                  {allowedTargets.map(t => (
                    <Chip key={t} label={t} onDelete={() => removeTarget(t)} />
                  ))}
                </Box>
              )}
            </Box>
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={submitting} color="inherit">
            Cancel
          </Button>
          <Button onClick={handleSubmit} disabled={submitting} variant="contained">
            {submitting ? (
              <>
                <CircularProgress size={20} sx={{ mr: 1 }} /> Saving...
              </>
            ) : (
              'Save Changes'
            )}
          </Button>
        </DialogActions>
      </Dialog>

      <ErrorAlert message={error} onClose={() => setError(null)} />
    </>
  );
}

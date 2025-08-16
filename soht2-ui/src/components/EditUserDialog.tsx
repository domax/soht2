/* SOHT2 © Licensed under MIT 2025. */
import { useState, useEffect } from 'react';
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
import { type ApiError, type Soht2User, UserApi, type UserRole } from '../api/soht2Api';
import AllowedTargets from '../controls/AllowedTargets';
import PasswordEye from '../controls/PasswordEye';

type EditUserDialogProps = Readonly<{ open: boolean; user: Soht2User | null; onClose: () => void }>;

export default function EditUserDialog({ open, user, onClose }: EditUserDialogProps) {
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<UserRole>(user?.role || 'USER');
  const [allowedTargets, setAllowedTargets] = useState<string[]>(user?.allowedTargets ?? []);
  const [initialRole] = useState<UserRole>(user?.role || 'USER');
  const [initialTargets] = useState<string[]>(user?.allowedTargets ?? []);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    // Reset when user changes/open toggles
    if (open) {
      setPassword('');
      setRole(user?.role || 'USER');
      setAllowedTargets(user?.allowedTargets ?? []);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, user?.username]);

  const handleSubmit = async () => {
    if (!user) return;
    setSubmitting(true);
    try {
      const params: {
        password?: string | null;
        role?: string | null;
        allowedTargets?: string[] | null;
      } = {};
      if (password) params.password = password; // optional; only send it if non-empty
      if (role && role !== initialRole) params.role = role;
      // Only send allowedTargets if changed.
      const changedTargets = JSON.stringify(allowedTargets) !== JSON.stringify(initialTargets);
      if (changedTargets) params.allowedTargets = allowedTargets;

      await UserApi.updateUser(user.username, params);
      window.dispatchEvent(
        new CustomEvent('users:changed', { detail: { action: 'update', username: user.username } })
      );
      onClose();
    } catch (e) {
      window.dispatchEvent(new CustomEvent<ApiError>(APP_ERROR_EVENT, { detail: e as ApiError }));
    } finally {
      setSubmitting(false);
    }
  };

  return (
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

          <PasswordEye
            password={password}
            helperText="Leave empty to keep unchanged"
            onChange={setPassword}
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

          <AllowedTargets targets={allowedTargets} onChange={setAllowedTargets} />
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
  );
}

/* SOHT2 © Licensed under MIT 2025. */
import { useCallback, useEffect, useState } from 'react';
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
import Select, { type SelectChangeEvent } from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import { type ApiError, type Soht2User, UserApi, type UserRole } from '../api/soht2Api';
import AllowedTargets from '../controls/AllowedTargets';
import PasswordField from '../controls/PasswordField';

type EditUserDialogProps = Readonly<{ open: boolean; user: Soht2User | null; onClose: () => void }>;

export default function EditUserDialog({ open, user, onClose }: EditUserDialogProps) {
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<UserRole>(user?.role || 'USER');
  const [allowedTargets, setAllowedTargets] = useState<string[]>(user?.allowedTargets ?? []);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    // Reset when user changes/open toggles
    if (open) {
      setPassword('');
      setRole(user?.role || 'USER');
      setAllowedTargets(user?.allowedTargets ?? []);
    }
  }, [open, user?.allowedTargets, user?.role]);

  const handleSubmit = useCallback(async () => {
    if (!user) return;
    setSubmitting(true);
    try {
      const params: {
        password?: string | null;
        role?: string | null;
        allowedTargets?: string[] | null;
      } = {};
      if (password) params.password = password; // optional; only send it if non-empty
      params.role = role;
      params.allowedTargets = allowedTargets;

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
  }, [user, password, role, allowedTargets, onClose]);

  const handleClose = useCallback(
    () => (!submitting ? onClose() : undefined),
    [onClose, submitting]
  );

  const handleChangeRole = useCallback(
    (e: SelectChangeEvent<UserRole>) => setRole(e.target.value as UserRole),
    []
  );

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle>Edit User</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label="Username"
            value={user?.username}
            slotProps={{ input: { readOnly: true } }}
          />

          <PasswordField
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
              onChange={handleChangeRole}>
              <MenuItem value="USER">USER</MenuItem>
              <MenuItem value="ADMIN">ADMIN</MenuItem>
            </Select>
          </FormControl>

          <AllowedTargets targets={allowedTargets} onChange={setAllowedTargets} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={submitting} color="inherit">
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

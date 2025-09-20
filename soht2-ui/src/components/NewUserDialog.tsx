/* SOHT2 © Licensed under MIT 2025. */
import { type ChangeEvent, useCallback, useEffect, useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Select, { type SelectChangeEvent } from '@mui/material/Select';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import { type ApiError, UserApi, type UserRole } from '../api/soht2Api';
import { dispatchAppErrorEvent, dispatchUserChangedEvent } from '../api/appEvents';
import AllowedTargets from '../controls/AllowedTargets';
import PasswordField from '../controls/PasswordField';

type NewUserForm = { username: string; password: string; role: UserRole; allowedTargets: string[] };

export default function NewUserDialog({
  open,
  onClose,
}: Readonly<{ open: boolean; onClose: () => void }>) {
  const [form, setForm] = useState<NewUserForm>({
    username: '',
    password: '',
    role: 'USER',
    allowedTargets: ['*:*'],
  });
  const [submitting, setSubmitting] = useState(false);

  const handleClose = useCallback(() => {
    if (!submitting) onClose();
  }, [submitting, onClose]);

  useEffect(() => {
    if (open) setForm({ username: '', password: '', role: 'USER', allowedTargets: ['*:*'] });
  }, [open]);

  const handleSubmit = useCallback(async () => {
    if (!form.username || !form.password) return;
    setSubmitting(true);
    try {
      await UserApi.createUser(form);
      dispatchUserChangedEvent('create', form.username);
      onClose();
    } catch (e) {
      dispatchAppErrorEvent(e as ApiError);
    } finally {
      setSubmitting(false);
    }
  }, [form, onClose]);

  const handleChangeUsername = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => setForm(prev => ({ ...prev, username: e.target.value })),
    []
  );

  const handleChangePassword = useCallback(
    (p: string) => setForm(prev => ({ ...prev, password: p })),
    []
  );

  const handleChangeRole = useCallback(
    (e: SelectChangeEvent<UserRole>) =>
      setForm(prev => ({ ...prev, role: e.target.value as UserRole })),
    []
  );

  const handleChangeTargets = useCallback(
    (targets: string[]) => setForm(prev => ({ ...prev, allowedTargets: targets })),
    []
  );

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle>New User</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label="Username"
            value={form.username}
            required
            onChange={handleChangeUsername}
            autoFocus
            autoComplete="username"
          />

          <PasswordField password={form.password} onChange={handleChangePassword} />

          <FormControl fullWidth>
            <InputLabel id="role-label">Role</InputLabel>
            <Select labelId="role-label" label="Role" value={form.role} onChange={handleChangeRole}>
              <MenuItem value="USER">USER</MenuItem>
              <MenuItem value="ADMIN">ADMIN</MenuItem>
            </Select>
          </FormControl>

          <AllowedTargets targets={form.allowedTargets} onChange={handleChangeTargets} />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={submitting} color="inherit">
          Cancel
        </Button>
        <Button
          onClick={handleSubmit}
          disabled={!form.username || !form.password || submitting}
          variant="contained">
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

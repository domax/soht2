/* SOHT2 © Licensed under MIT 2025. */
import { useCallback, useEffect, useMemo, useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import { APP_ERROR_EVENT } from './ErrorAlert';
import { ApiError, UserApi } from '../api/soht2Api';
import PasswordField from '../controls/PasswordField';

export default function ChangePasswordDialog({
  open,
  onClose,
}: Readonly<{ open: boolean; onClose: () => void }>) {
  const [oldPassword, setOldPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [loading, setLoading] = useState(false);

  const empty = useMemo(() => !oldPassword || !newPassword, [newPassword, oldPassword]);

  useEffect(() => {
    // Reset when user changes/open toggles
    if (open) {
      setOldPassword('');
      setNewPassword('');
    }
  }, [open]);

  const handleSubmit = useCallback(async () => {
    if (oldPassword === newPassword) {
      window.dispatchEvent(
        new CustomEvent<ApiError>(APP_ERROR_EVENT, {
          detail: new ApiError('New password must be different'),
        })
      );
      return;
    }
    setLoading(true);
    try {
      await UserApi.changePassword({ old: oldPassword, new: newPassword });
      // on success close and reset
      setOldPassword('');
      setNewPassword('');
      onClose();
    } catch (e) {
      window.dispatchEvent(new CustomEvent<ApiError>(APP_ERROR_EVENT, { detail: e as ApiError }));
    } finally {
      setLoading(false);
    }
  }, [newPassword, oldPassword, onClose]);

  const handleClose = useCallback(() => {
    if (!loading) onClose();
  }, [loading, onClose]);

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="xs">
      <DialogTitle>Change Password</DialogTitle>
      <DialogContent>
        <PasswordField
          label="Old Password"
          password={oldPassword}
          fullWidth
          margin="normal"
          autoComplete="current-password"
          onChange={setOldPassword}
        />
        <PasswordField
          label="New Password"
          password={newPassword}
          fullWidth
          margin="normal"
          onChange={setNewPassword}
          onEnter={() => {
            if (!empty && !loading) void handleSubmit();
          }}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={loading} color="inherit">
          Cancel
        </Button>
        <Button onClick={handleSubmit} disabled={empty || loading} variant="contained">
          {loading ? (
            <>
              <CircularProgress size={20} sx={{ mr: 1 }} /> Changing...
            </>
          ) : (
            'Change Password'
          )}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

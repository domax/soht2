import React from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import { APP_ERROR_EVENT } from './ErrorAlert';
import CircularProgress from '@mui/material/CircularProgress';
import { UserApi, ApiError } from '../api/soht2Api';

export default function ChangePasswordDialog({
  open,
  onClose,
}: Readonly<{ open: boolean; onClose: () => void }>) {
  const [oldPassword, setOldPassword] = React.useState('');
  const [newPassword, setNewPassword] = React.useState('');
  const [confirmPassword, setConfirmPassword] = React.useState('');
  const [loading, setLoading] = React.useState(false);

  const empty = !oldPassword || !newPassword || !confirmPassword;

  const handleSubmit = async () => {
    if (newPassword !== confirmPassword) {
      window.dispatchEvent(
        new CustomEvent<ApiError>(APP_ERROR_EVENT, {
          detail: new ApiError(new Date().toISOString(), 400, 'New passwords do not match'),
        })
      );
      return;
    }
    if (oldPassword === newPassword) {
      window.dispatchEvent(
        new CustomEvent<ApiError>(APP_ERROR_EVENT, {
          detail: new ApiError(new Date().toISOString(), 400, 'New password must be different'),
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
      setConfirmPassword('');
      onClose();
    } catch (e) {
      window.dispatchEvent(new CustomEvent<ApiError>(APP_ERROR_EVENT, { detail: e as ApiError }));
    } finally {
      setLoading(false);
    }
  };

  const handleClose = () => {
    if (!loading) onClose();
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="xs">
      <DialogTitle>Change Password</DialogTitle>
      <DialogContent>
        <TextField
          label="Old Password"
          type="password"
          variant="outlined"
          fullWidth
          margin="normal"
          value={oldPassword}
          required
          onChange={e => {
            setOldPassword(e.target.value);
          }}
          autoComplete="current-password"
        />
        <TextField
          label="New Password"
          type="password"
          variant="outlined"
          fullWidth
          margin="normal"
          value={newPassword}
          required
          onChange={e => {
            setNewPassword(e.target.value);
          }}
          autoComplete="new-password"
        />
        <TextField
          label="Confirm New Password"
          type="password"
          variant="outlined"
          fullWidth
          margin="normal"
          value={confirmPassword}
          required
          onChange={e => {
            setConfirmPassword(e.target.value);
          }}
          autoComplete="new-password"
          onKeyDown={e => {
            if (e.key === 'Enter' && !empty && !loading) handleSubmit();
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

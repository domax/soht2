/* SOHT2 © Licensed under MIT 2025. */
import React from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import Button from '@mui/material/Button';
import Box from '@mui/material/Box';
import { APP_ERROR_EVENT } from './ErrorAlert';
import { type ApiError, type Soht2User, UserApi } from '../api/soht2Api';

type DeleteUserDialogProps = Readonly<{
  open: boolean;
  user: Soht2User | null;
  onClose: () => void;
}>;

export default function DeleteUserDialog({ open, user, onClose }: DeleteUserDialogProps) {
  const [deleteHistory, setDeleteHistory] = React.useState(true);
  const [deleteForce, setDeleteForce] = React.useState(false);
  const [deleting, setDeleting] = React.useState(false);

  React.useEffect(() => {
    if (open) {
      setDeleteHistory(true);
      setDeleteForce(false);
      setDeleting(false);
    }
  }, [open, user?.username]);

  const handleConfirm = async () => {
    if (!user) return;
    setDeleting(true);
    try {
      await UserApi.deleteUser(user.username, { force: deleteForce, history: deleteHistory });
      window.dispatchEvent(
        new CustomEvent('users:changed', { detail: { action: 'delete', username: user.username } })
      );
      onClose();
    } catch (e) {
      window.dispatchEvent(new CustomEvent<ApiError>(APP_ERROR_EVENT, { detail: e as ApiError }));
    } finally {
      setDeleting(false);
    }
  };

  return (
    <Dialog open={open} onClose={() => (!deleting ? onClose() : undefined)}>
      <DialogTitle>Delete User</DialogTitle>
      <DialogContent>
        <Box sx={{ mt: 1 }}>Are you sure you want to delete user "{user?.username}"?</Box>
        <Box sx={{ mt: 1 }}>
          <FormControlLabel
            control={
              <Checkbox
                checked={deleteHistory}
                onChange={e => setDeleteHistory(e.target.checked)}
              />
            }
            label="Remove history"
          />
        </Box>
        {(user?.role || '').toUpperCase() === 'ADMIN' && (
          <Box>
            <FormControlLabel
              control={
                <Checkbox checked={deleteForce} onChange={e => setDeleteForce(e.target.checked)} />
              }
              label="Force deletion"
            />
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={deleting} color="inherit">
          Cancel
        </Button>
        <Button onClick={handleConfirm} disabled={deleting} color="error" variant="contained">
          {deleting ? 'Deleting...' : 'Delete'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

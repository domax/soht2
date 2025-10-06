/* SOHT2 © Licensed under MIT 2025. */
import { type ChangeEvent, useCallback, useEffect, useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import Button from '@mui/material/Button';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';
import { type ApiError, type Soht2User, UserApi } from '../api/soht2Api';
import { dispatchAppErrorEvent, dispatchUserChangedEvent } from '../api/appEvents';

export default function DeleteUserDialog({
  open,
  user,
  onClose,
}: Readonly<{ open: boolean; user: Soht2User | null; onClose: () => void }>) {
  const [deleteHistory, setDeleteHistory] = useState(true);
  const [deleteForce, setDeleteForce] = useState(false);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (open) {
      setDeleteHistory(true);
      setDeleteForce(false);
      setDeleting(false);
    }
  }, [open, user?.username]);

  const handleConfirm = useCallback(async () => {
    if (!user) return;
    setDeleting(true);
    try {
      await UserApi.deleteUser(user.username, { force: deleteForce, history: deleteHistory });
      dispatchUserChangedEvent('delete', user.username);
      onClose();
    } catch (e) {
      dispatchAppErrorEvent(e as ApiError);
    } finally {
      setDeleting(false);
    }
  }, [user, deleteHistory, deleteForce, onClose]);

  const handleClose = useCallback(() => {
    if (!deleting) onClose();
  }, [deleting, onClose]);

  const handleChangeHistory = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => setDeleteHistory(e.target.checked),
    []
  );

  const handleChangeForce = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => setDeleteForce(e.target.checked),
    []
  );

  return (
    <Dialog open={open} onClose={handleClose}>
      <DialogTitle>Delete User</DialogTitle>
      <DialogContent>
        <Box sx={{ mt: 1 }}>Are you sure you want to delete user "{user?.username}"?</Box>
        <Box sx={{ mt: 1 }}>
          <FormControlLabel
            control={<Checkbox checked={deleteHistory} onChange={handleChangeHistory} />}
            label="Remove history"
          />
        </Box>
        {(user?.role ?? '').toUpperCase() === 'ADMIN' && (
          <Box>
            <FormControlLabel
              control={<Checkbox checked={deleteForce} onChange={handleChangeForce} />}
              label="Force deletion"
            />
          </Box>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={deleting} color="inherit">
          Cancel
        </Button>
        <Button onClick={handleConfirm} disabled={deleting} color="error" variant="contained">
          {deleting ? (
            <>
              <CircularProgress size={20} sx={{ mr: 1 }} /> Deleting...
            </>
          ) : (
            'Delete'
          )}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

import { useCallback, useEffect, useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import Box from '@mui/material/Box';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import { type ApiError, ConnectionApi, type UUID } from '../api/soht2Api';
import { dispatchAppErrorEvent, dispatchConnectionChangedEvent } from '../api/appEvents';

export default function ConnectionCloseDialog({
  open,
  connectionId,
  onClose,
}: Readonly<{ open: boolean; connectionId?: UUID | null; onClose: () => void }>) {
  const [closing, setClosing] = useState(false);

  useEffect(() => {
    if (open) setClosing(false);
  }, [open]);

  const handleConfirm = useCallback(async () => {
    if (!connectionId) return;
    setClosing(true);

    try {
      await ConnectionApi.close(connectionId);
      dispatchConnectionChangedEvent('close', connectionId);
      onClose();
    } catch (e) {
      dispatchAppErrorEvent(e as ApiError);
    } finally {
      setClosing(false);
    }
  }, [connectionId, onClose]);

  const handleClose = useCallback(() => {
    if (!closing) onClose();
  }, [closing, onClose]);

  return (
    <Dialog open={open} onClose={handleClose}>
      <DialogTitle>Close Connection</DialogTitle>
      <DialogContent>
        <Box sx={{ mt: 1 }}>
          Are you sure you want to close this connection?
          <br /> <pre>{connectionId}</pre>
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={closing} color="inherit">
          Cancel
        </Button>
        <Button onClick={handleConfirm} disabled={closing} color="error" variant="contained">
          {closing ? (
            <>
              <CircularProgress size={20} sx={{ mr: 1 }} /> Closing...
            </>
          ) : (
            'Close Connection'
          )}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

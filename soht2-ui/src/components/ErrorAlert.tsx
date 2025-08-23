/* SOHT2 © Licensed under MIT 2025. */
import { useCallback, useState } from 'react';
import Snackbar from '@mui/material/Snackbar';
import Alert from '@mui/material/Alert';
import { AppErrorEvent } from '../api/appEvents';
import { useEventListener } from '../hooks';

export default function ErrorAlert() {
  const [message, setMessage] = useState<string | null>(null);

  useEventListener(AppErrorEvent.TYPE, (e: AppErrorEvent) =>
    setMessage(e.detail?.errors?.[0]?.defaultMessage ?? e.detail?.message ?? 'Unexpected error')
  );

  const onClose = useCallback(() => setMessage(null), []);

  return (
    <Snackbar
      open={!!message}
      autoHideDuration={10000}
      onClose={onClose}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}>
      <Alert onClose={onClose} severity="error" variant="filled" sx={{ width: '100%' }}>
        <div style={{ whiteSpace: 'pre-wrap' }}>{message}</div>
      </Alert>
    </Snackbar>
  );
}

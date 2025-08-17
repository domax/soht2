/* SOHT2 © Licensed under MIT 2025. */
import { useCallback, useEffect, useState } from 'react';
import Snackbar from '@mui/material/Snackbar';
import Alert from '@mui/material/Alert';
import type { ApiError } from '../api/soht2Api';

export const APP_ERROR_EVENT = 'app:error';

export default function ErrorAlert() {
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    const handler = (evt: CustomEvent<ApiError>) =>
      setMessage(
        evt.detail?.errors?.[0]?.defaultMessage || evt.detail?.message || 'Unexpected error'
      );
    window.addEventListener(APP_ERROR_EVENT, handler as EventListener);
    return () => window.removeEventListener(APP_ERROR_EVENT, handler as EventListener);
  }, []);

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

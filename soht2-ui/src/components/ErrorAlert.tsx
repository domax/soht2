/* SOHT2 © Licensed under MIT 2025. */
import React from 'react';
import Snackbar from '@mui/material/Snackbar';
import Alert from '@mui/material/Alert';
import type { SnackbarOrigin } from '@mui/material/Snackbar';
import type { ApiError } from '../api/soht2Api';

export const APP_ERROR_EVENT = 'app:error';

export type ErrorAlertProps = Readonly<{
  autoHideDuration?: number;
  anchorOrigin?: SnackbarOrigin;
}>;

export default function ErrorAlert({
  autoHideDuration = 10000,
  anchorOrigin = { vertical: 'bottom', horizontal: 'center' },
}: ErrorAlertProps) {
  const [message, setMessage] = React.useState<string | null>(null);

  React.useEffect(() => {
    const handler = (evt: Event) => {
      const ce = evt as CustomEvent<ApiError>;
      const msg =
        ce.detail?.errors?.[0]?.defaultMessage || ce.detail?.message || 'Unexpected error';
      setMessage(msg);
    };
    window.addEventListener(APP_ERROR_EVENT, handler as EventListener);
    return () => window.removeEventListener(APP_ERROR_EVENT, handler as EventListener);
  }, []);

  const onClose = () => setMessage(null);

  return (
    <Snackbar
      open={!!message}
      autoHideDuration={autoHideDuration}
      onClose={onClose}
      anchorOrigin={anchorOrigin}>
      <Alert onClose={onClose} severity="error" variant="filled" sx={{ width: '100%' }}>
        <div style={{ whiteSpace: 'pre-wrap' }}>{message}</div>
      </Alert>
    </Snackbar>
  );
}

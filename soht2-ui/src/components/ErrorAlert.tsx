import Snackbar from '@mui/material/Snackbar';
import Alert from '@mui/material/Alert';
import type { SnackbarOrigin } from '@mui/material/Snackbar';

export type ErrorAlertProps = Readonly<{
  message: string | null;
  onClose: () => void;
  autoHideDuration?: number;
  anchorOrigin?: SnackbarOrigin;
}>;

export default function ErrorAlert({
  message,
  onClose,
  autoHideDuration = 10000,
  anchorOrigin = { vertical: 'bottom', horizontal: 'center' },
}: ErrorAlertProps) {
  return (
    <Snackbar
      open={!!message}
      autoHideDuration={autoHideDuration}
      onClose={onClose}
      anchorOrigin={anchorOrigin}>
      <Alert
        onClose={onClose}
        severity="error"
        variant="filled"
        sx={{ width: '100%', zIndex: 10000 }}>
        <div style={{ whiteSpace: 'pre-wrap' }}>{message}</div>
      </Alert>
    </Snackbar>
  );
}

/* SOHT2 © Licensed under MIT 2025. */
import type { ReactNode } from 'react';
import type { NoRowsOverlayPropsOverrides } from '@mui/x-data-grid';
import { useTheme } from '@mui/material/styles';
import Box from '@mui/material/Box';
import Alert from '@mui/material/Alert';
import CircularProgress from '@mui/material/CircularProgress';

declare module '@mui/x-data-grid' {
  interface NoRowsOverlayPropsOverrides {
    message: string;
  }
}

function OverlayBase({ children }: Readonly<{ children: ReactNode }>) {
  const { mode, grey } = useTheme().palette;
  return (
    <Box
      sx={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        height: '100%',
        backgroundColor: `${mode === 'light' ? grey[100] : grey[900]}`,
      }}>
      {children}
    </Box>
  );
}

function NoRowsOverlay({ message }: Readonly<NoRowsOverlayPropsOverrides>) {
  return (
    <OverlayBase>
      <Alert severity="info">{message ?? 'No records available to display.'}</Alert>
    </OverlayBase>
  );
}

function LoadingOverlay() {
  return (
    <OverlayBase>
      <CircularProgress />
    </OverlayBase>
  );
}

export { NoRowsOverlay, LoadingOverlay };

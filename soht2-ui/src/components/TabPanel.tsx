/* SOHT2 © Licensed under MIT 2025. */
import { type ReactNode } from 'react';
import Box from '@mui/material/Box';

export default function TabPanel({
  children,
  prefix = '',
  index,
  value,
}: Readonly<{ children?: ReactNode; prefix?: string; index: number; value: number }>) {
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`${prefix}tabpanel-${index}`}
      aria-labelledby={`${prefix}tab-${index}`}
      style={{ height: '100%' }}>
      {value === index && <Box sx={{ p: 0, height: '100%' }}>{children}</Box>}
    </div>
  );
}

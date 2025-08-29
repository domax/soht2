/* SOHT2 © Licensed under MIT 2025. */
import type { ISODateTime } from './soht2Api';
import type { SxProps } from '@mui/system';
import { type Theme } from '@mui/material/styles';
import { gridClasses } from '@mui/x-data-grid';

function dataGridStyle(theme: Theme): SxProps<Theme> {
  const { mode, grey } = theme.palette;
  return {
    [`& .${gridClasses.cell}:focus, & .${gridClasses.cell}:focus-within`]: { outline: 'none' },
    [`& .${gridClasses.columnHeader}:focus, & .${gridClasses.columnHeader}:focus-within`]: {
      outline: 'none',
    },
    '& .MuiDataGrid-columnHeaderTitle': { fontWeight: 'bold' },
    '& .MuiDataGrid-virtualScrollerContent': {
      backgroundColor: `${mode === 'light' ? grey[100] : grey[900]}`,
    },
    '& .MuiDataGrid-row:hover': { backgroundColor: `${mode === 'light' ? grey[300] : grey[800]}` },
  };
}

function formatBytes(bytes: number, decimals: number = 2): string {
  if (!+bytes) return '0 Bytes';

  const k = 1024;
  const dm = decimals < 0 ? 0 : decimals;
  const sizes = ['Bytes', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB', 'ZiB', 'YiB'];

  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${parseFloat((bytes / Math.pow(k, i)).toFixed(dm))} ${sizes[i]}`;
}

function asTime(t?: ISODateTime | null) {
  return t ? new Date(t).getTime() : 0;
}

function asNumber(n?: number | null) {
  return Number(n ?? 0);
}

function asString(s?: string | null) {
  return s ? s.toLowerCase() : '';
}

function compareTimes(a?: ISODateTime | null, b?: ISODateTime | null) {
  return asTime(a) - asTime(b);
}

function compareNumbers(a?: number | null, b?: number | null) {
  return asNumber(a) - asNumber(b);
}

function compareStrings(a?: string | null, b?: string | null) {
  return asString(a).localeCompare(asString(b));
}

export { dataGridStyle, formatBytes, compareTimes, compareNumbers, compareStrings };

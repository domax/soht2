/* SOHT2 © Licensed under MIT 2025. */
import type { SxProps } from '@mui/system';
import { type Theme } from '@mui/material/styles';
import { gridClasses, type GridFilterItem, type GridFilterOperator } from '@mui/x-data-grid';
import type { ISODateTime } from './soht2Api';
import DateTimeGridFilter from '../controls/DateTimeGridFilter';
import DateTimeRangeGridFilter from '../controls/DateTimeRangeGridFilter';

function formatBytes(bytes: number, decimals: number = 2): string {
  if (!+bytes) return '0 Bytes';

  const k = 1024;
  const dm = Math.max(decimals, 0);
  const sizes = ['Bytes', 'KiB', 'MiB', 'GiB', 'TiB', 'PiB', 'EiB', 'ZiB', 'YiB'];

  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return `${Number.parseFloat((bytes / Math.pow(k, i)).toFixed(dm))} ${sizes[i]}`;
}

function formatDateTime(timestamp: Date | ISODateTime): string {
  const d = timestamp instanceof Date ? timestamp : new Date(timestamp);
  return d.toLocaleString(undefined, {
    year: '2-digit',
    month: 'numeric',
    day: 'numeric',
    hour: 'numeric',
    minute: 'numeric',
  });
}

function getDataGridStyle(theme: Theme): SxProps<Theme> {
  const { mode, grey } = theme.palette;
  return {
    border: 0,
    height: 'calc(100vh - 130px)',
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

function getDateTimeGridFilterStyle(): SxProps<Theme> {
  return {
    width: 200,
    ['& label']: { transform: 'translate(14px, 10px) scale(1)' },
    ['& label.Mui-focused']: { transform: 'translate(14px, -9px) scale(0.75)' },
    ['& label.MuiFormLabel-filled']: { transform: 'translate(14px, -9px) scale(0.75)' },
    ['& .MuiPickersSectionList-root']: { paddingY: '8.5px' },
  };
}

function getDateTimeOperators(): GridFilterOperator[] {
  return [
    {
      label: 'after',
      value: 'after',
      getApplyFilterFn: () => null,
      InputComponent: DateTimeGridFilter,
    },
    {
      label: 'before',
      value: 'before',
      getApplyFilterFn: () => null,
      InputComponent: DateTimeGridFilter,
    },
    {
      label: 'between',
      value: 'between',
      getApplyFilterFn: () => null,
      InputComponent: DateTimeRangeGridFilter,
    },
  ];
}

function getISODateTime(d?: Date, withOffset: boolean = false): ISODateTime | undefined {
  if (!d) return undefined;
  const pad = (num: number, len: number = 2) => String(num).padStart(len, '0');

  const year = d.getFullYear();
  const month = pad(d.getMonth() + 1);
  const day = pad(d.getDate());
  const hours = pad(d.getHours());
  const minutes = pad(d.getMinutes());
  const seconds = pad(d.getSeconds());
  const millis = pad(d.getMilliseconds(), 3);

  let offset: string = '';
  if (withOffset) {
    const offsetMinutes = d.getTimezoneOffset();
    const absOffset = Math.abs(offsetMinutes);
    const offsetHours = pad(Math.floor(absOffset / 60));
    const offsetMins = pad(absOffset % 60);
    const sign = offsetMinutes <= 0 ? '+' : '-';
    offset = `${sign}${offsetHours}:${offsetMins}`;
  }
  return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}.${millis}${offset}`;
}

type DateTimeFilter = { after?: ISODateTime; before?: ISODateTime };
function getDateTimeFilter(i?: GridFilterItem): DateTimeFilter {
  const result: DateTimeFilter = {};
  if (!i?.value) return result;

  switch (i.operator) {
    case 'after':
      result.after = getISODateTime(i.value);
      break;
    case 'before':
      result.before = getISODateTime(i.value);
      break;
    default:
      if (Array.isArray(i.value) && i.value[0] && i.value[1]) {
        result.after = getISODateTime(i.value[0]);
        result.before = getISODateTime(i.value[1]);
      }
  }
  return result;
}

function getStringFilter(i?: GridFilterItem): string | undefined {
  if (!i?.value) return undefined;
  switch (i.operator) {
    case 'equals':
      return i.value;
    case 'startsWith':
      return `${i.value}*`;
    case 'endsWith':
      return `*${i.value}`;
    default:
      return `*${i.value}*`;
  }
}

function getNumberArrayFilter(i?: GridFilterItem): number[] | undefined {
  if (!i?.value) return undefined;
  return Array.isArray(i.value) ? i.value.map(Number) : [Number(i.value)];
}

function getStringFilterItem(field: string, v?: string): GridFilterItem | undefined {
  if (!v) return undefined;
  if (v.startsWith('*') && v.endsWith('*'))
    return { field, operator: 'contains', value: v.slice(1, -1) };
  if (v.endsWith('*')) return { field, operator: 'startsWith', value: v.slice(0, -1) };
  if (v.startsWith('*')) return { field, operator: 'endsWith', value: v.slice(1) };
  return { field, operator: 'equals', value: v };
}

function getPortFilterItem(field: string, v?: number[]): GridFilterItem | undefined {
  if (!v || v.length === 0) return undefined;
  if (v.length === 1) return { field, operator: '=', value: v[0] };
  return { field, operator: 'isAnyOf', value: v };
}

function getDateTimeFilterItem(
  field: string,
  va?: ISODateTime,
  vb?: ISODateTime
): GridFilterItem | undefined {
  if (va && vb) return { field, operator: 'between', value: [new Date(va), new Date(vb)] };
  if (va && !vb) return { field, operator: 'after', value: new Date(va) };
  if (!va && vb) return { field, operator: 'before', value: new Date(vb) };
  return undefined;
}

export {
  formatBytes,
  formatDateTime,
  getDataGridStyle,
  getDateTimeGridFilterStyle,
  getDateTimeOperators,
  getISODateTime,
  type DateTimeFilter,
  getDateTimeFilter,
  getStringFilter,
  getNumberArrayFilter,
  getStringFilterItem,
  getPortFilterItem,
  getDateTimeFilterItem,
};

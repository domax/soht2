/* SOHT2 © Licensed under MIT 2025. */
import { useCallback, useMemo } from 'react';
import type { GridFilterInputValueProps } from '@mui/x-data-grid';
import Box from '@mui/material/Box';
import dayjs from 'dayjs';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker';
import type { PickerValue } from '@mui/x-date-pickers/internals';
import { getDateTimeGridFilterStyle } from '../api/functions';

export default function DateTimeGridFilter({
  item,
  applyValue,
  focusElementRef = null,
}: Readonly<GridFilterInputValueProps>) {
  const handleFilterChange = useCallback(
    (v: PickerValue) => applyValue({ ...item, value: v ? v.toDate() : undefined }),
    [applyValue, item]
  );

  const sx = useMemo(getDateTimeGridFilterStyle, []);

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <Box sx={{ display: 'inline-flex', flexDirection: 'row', alignItems: 'end' }}>
        <DateTimePicker
          label="Value"
          name="grid-filter-date-time-picker"
          format="MM/DD/YY hh:mm A"
          sx={sx}
          defaultValue={item.value ? dayjs(item.value) : undefined}
          onChange={handleFilterChange}
          inputRef={focusElementRef}
        />
      </Box>
    </LocalizationProvider>
  );
}

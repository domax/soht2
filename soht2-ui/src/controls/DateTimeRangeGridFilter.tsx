/* SOHT2 © Licensed under MIT 2025. */
import { useCallback, useEffect, useMemo, useState } from 'react';
import { type GridFilterInputValueProps } from '@mui/x-data-grid';
import Box from '@mui/material/Box';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import { LocalizationProvider } from '@mui/x-date-pickers';
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker';
import type { PickerValue } from '@mui/x-date-pickers/internals';
import dayjs from 'dayjs';
import { getDateTimeGridFilterStyle } from '../api/functions';

export default function DateTimeRangeGridFilter(props: Readonly<GridFilterInputValueProps>) {
  const { item, applyValue, focusElementRef = null } = props;

  const [filterValue, setFilterValue] = useState<[Date | undefined, Date | undefined]>(
    item.value ?? []
  );
  useEffect(() => setFilterValue(item.value ?? []), [item.value]);

  const updateFilterValue = useCallback(
    (lowerBound: Date | undefined, upperBound: Date | undefined) => {
      const value: [Date | undefined, Date | undefined] = [lowerBound, upperBound];
      setFilterValue(value);
      applyValue({ ...item, value });
    },
    [applyValue, item]
  );

  const handleLowerFilterChange = useCallback(
    (value: PickerValue) => updateFilterValue(value ? value.toDate() : undefined, filterValue[1]),
    [filterValue, updateFilterValue]
  );

  const handleUpperFilterChange = useCallback(
    (value: PickerValue) => updateFilterValue(filterValue[0], value ? value.toDate() : undefined),
    [filterValue, updateFilterValue]
  );

  const sx = useMemo(getDateTimeGridFilterStyle, []);

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <Box sx={{ display: 'inline-flex', flexDirection: 'row', alignItems: 'end', gap: 1.5 }}>
        <DateTimePicker
          label="From"
          format="MM/DD/YY hh:mm a"
          name="grid-filter-lower-date-time-picker"
          sx={sx}
          defaultValue={filterValue[0] ? dayjs(filterValue[0]) : undefined}
          onChange={handleLowerFilterChange}
          inputRef={focusElementRef}
        />
        <DateTimePicker
          label="To"
          format="MM/DD/YY hh:mm a"
          name="grid-filter-upper-date-time-picker"
          sx={sx}
          defaultValue={filterValue[1] ? dayjs(filterValue[1]) : undefined}
          onChange={handleUpperFilterChange}
        />
      </Box>
    </LocalizationProvider>
  );
}

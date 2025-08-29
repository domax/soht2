import { useCallback } from 'react';
import type { GridFilterInputValueProps } from '@mui/x-data-grid';
import { DateTimePicker } from '@mui/x-date-pickers/DateTimePicker';
import { LocalizationProvider } from '@mui/x-date-pickers/LocalizationProvider';
import { AdapterDayjs } from '@mui/x-date-pickers/AdapterDayjs';
import dayjs from 'dayjs';
import type { PickerValue } from '@mui/x-date-pickers/internals';

export default function DateTimeGridFilter(props: Readonly<GridFilterInputValueProps>) {
  const { item, applyValue } = props;

  const handleFilterChange = useCallback(
    (value: PickerValue) => {
      applyValue({ ...item, value: value ? value.toDate() : undefined });
    },
    [applyValue, item]
  );

  return (
    <LocalizationProvider dateAdapter={AdapterDayjs}>
      <DateTimePicker
        name="grid-filter-date-time-picker"
        sx={{
          width: '220px',
          ['& .MuiPickersSectionList-root, & .MuiPickersInputBase-sectionsContainer']: {
            paddingY: '8.5px',
          },
        }}
        defaultValue={item.value ? dayjs(item.value) : undefined}
        onChange={handleFilterChange}
      />
    </LocalizationProvider>
  );
}

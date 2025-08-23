/* SOHT2 © Licensed under MIT 2025. */
import { type TableSorting } from '../api/soht2Api.ts';
import { useCallback } from 'react';
import TableCell from '@mui/material/TableCell';
import TableSortLabel from '@mui/material/TableSortLabel';
import FunnelIcon from '@mui/icons-material/FilterAlt';

function toggleTableSort<SortColumn extends string>(
  column: SortColumn | null,
  sorting: TableSorting<SortColumn>
): TableSorting<SortColumn> {
  if (sorting.column === column) {
    if (sorting.direction === 'asc') return { column, direction: 'desc' };
    if (sorting.direction === 'desc') return { column: null, direction: null };
  }
  return { column, direction: 'asc' };
}

export default function TableHeaderCell<SortColumn extends string>({
  label,
  sorting,
  column,
  hasFilter = false,
  onSortingChange,
}: Readonly<{
  label: string;
  sorting: TableSorting<SortColumn>;
  column: SortColumn | null;
  hasFilter?: boolean;
  onSortingChange: (s: TableSorting<SortColumn>) => void;
}>) {
  const toggleSort = useCallback(
    () => onSortingChange(toggleTableSort(column, sorting)),
    [column, onSortingChange, sorting]
  );
  const sortDir = sorting.direction ?? false;
  const dir = sorting.direction ?? 'asc';
  return (
    <TableCell sortDirection={sorting.column === column ? sortDir : false}>
      <TableSortLabel active={sorting.column === column} direction={dir} onClick={toggleSort}>
        {hasFilter && <FunnelIcon fontSize="inherit" sx={{ mr: 0.5 }} />}
        <b>{label}</b>
      </TableSortLabel>
    </TableCell>
  );
}

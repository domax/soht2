/* SOHT2 © Licensed under MIT 2025. */
import { type MouseEvent, useCallback, useEffect, useMemo, useState } from 'react';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Box from '@mui/material/Box';
import Alert from '@mui/material/Alert';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import ListItemIcon from '@mui/material/ListItemIcon';
import RefreshIcon from '@mui/icons-material/Refresh';
import Stack from '@mui/material/Stack';
import Pagination from '@mui/material/Pagination';
import TextField from '@mui/material/TextField';
import InputAdornment from '@mui/material/InputAdornment';
import FunnelIcon from '@mui/icons-material/FilterAlt';
import CircularProgress from '@mui/material/CircularProgress';
import HeaderMenuButton from '../controls/HeaderMenuButton';
import {
  type ApiError,
  ConnectionApi,
  type HistoryPage,
  type HistorySortColumn,
  type SortingDirLower,
  type TableSorting,
} from '../api/soht2Api';
import { formatBytes } from '../api/functions';
import { useDebounce } from '../hooks';
import HistoryFiltersDialog, { type HistoryFilters } from './HistoryFiltersDialog';
import TableHeaderCell from './TableHeaderCell';

export type HistoryTableSorting = TableSorting<HistorySortColumn>;
export type HistoryNavigation = HistoryFilters & { sort?: string[]; pg?: number; sz?: number };

function HistoryTableCell({
  label,
  sorting,
  column,
  filters,
  keys = [],
  onSortingChange,
}: Readonly<{
  label: string;
  sorting: HistoryTableSorting;
  column: HistorySortColumn | null;
  filters: HistoryFilters;
  keys?: (keyof HistoryFilters)[];
  onSortingChange: (s: HistoryTableSorting) => void;
}>) {
  const hasFilter = useMemo(
    () =>
      keys.some(k => {
        const v = (filters as Record<string, unknown>)[k];
        if (Array.isArray(v)) return v.length > 0;
        return v !== undefined && v !== null && v !== '';
      }),
    [filters, keys]
  );
  return (
    <TableHeaderCell
      label={label}
      sorting={sorting}
      column={column}
      onSortingChange={onSortingChange}
      hasFilter={hasFilter}
    />
  );
}

export default function HistoryTable({
  regularUser,
  navigation,
  onNavigationChange,
}: Readonly<{
  regularUser?: string;
  navigation?: HistoryNavigation;
  onNavigationChange?: (n: HistoryNavigation) => void;
}>) {
  const [menuHeaderAnchor, setMenuHeaderAnchor] = useState<HTMLElement | null>(null);
  const handleMenuHeaderOpen = useCallback((e: MouseEvent<HTMLElement>) => {
    setMenuHeaderAnchor(e.currentTarget);
  }, []);
  const handleMenuHeaderClose = useCallback(() => setMenuHeaderAnchor(null), []);

  const [filtersOpen, setFiltersOpen] = useState(false);
  const [filters, setFilters] = useState<HistoryFilters>(navigation ?? {});

  const [page, setPage] = useState<number>(navigation?.pg ?? 0); // 0-based for API
  const navPageSize = navigation?.sz ?? 50;
  const [pageSize, setPageSize] = useState<number>(navPageSize);
  const [pageSizeInput, setPageSizeInput] = useState<string>(String(navPageSize));

  const [navSorting] = navigation?.sort ?? ['openedAt:desc'];
  const [navColumn, navDir] = navSorting.split(':');
  const [sorting, setSorting] = useState<HistoryTableSorting>({
    column: navColumn as HistorySortColumn,
    direction: navDir as SortingDirLower,
  });

  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [pageData, setPageData] = useState<HistoryPage | null>(null);

  const asSortArray = useMemo(() => {
    if (!sorting.column || !sorting.direction) return undefined;
    return [`${sorting.column}:${sorting.direction}`];
  }, [sorting]);

  useEffect(() => {
    if (onNavigationChange)
      onNavigationChange({ ...filters, sort: asSortArray, pg: page, sz: pageSize });
  }, [filters, asSortArray, page, pageSize, onNavigationChange]);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const res = await ConnectionApi.history({
        ...filters,
        sort: asSortArray,
        pg: page,
        sz: pageSize,
      });
      setPageData(res);
    } catch (e) {
      const apiError = e as ApiError;
      setError(apiError.message);
    } finally {
      setLoading(false);
    }
  }, [filters, asSortArray, page, pageSize]);

  useEffect(() => void load(), [load]);

  const handleRefresh = useCallback(() => {
    handleMenuHeaderClose();
    void load();
  }, [handleMenuHeaderClose, load]);

  const handleOpenFilters = useCallback(() => {
    handleMenuHeaderClose();
    setFiltersOpen(true);
  }, [handleMenuHeaderClose]);

  const handleFiltersApply = useCallback((f: HistoryFilters) => {
    setFilters(f);
    setPage(0);
    setFiltersOpen(false);
  }, []);

  const handlePageSizeInput = useCallback(() => {
    let n = Number(pageSizeInput);
    if (Number.isNaN(n)) n = 50;
    if (n < 1) n = 1;
    if (n > 1000) n = 1000;
    setPageSize(n);
    if (pageData?.paging?.pageSize !== n) setPage(0);
  }, [pageData?.paging?.pageSize, pageSizeInput]);

  useDebounce(handlePageSizeInput, 700, [handlePageSizeInput]);

  useEffect(() => setPageSizeInput(String(pageSize)), [pageSize]);

  const onFiltersClose = useCallback(() => setFiltersOpen(false), []);

  const totalRows = pageData?.totalItems ?? 0;
  const totalPages = pageData?.totalPages ?? 0;
  const data = pageData?.data ?? [];

  if (loading) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
        <CircularProgress />
      </Box>
    );
  }

  return (
    <>
      <Stack
        direction="row"
        spacing={2}
        alignItems="center"
        justifyContent="space-between"
        sx={{ paddingTop: 2 }}>
        <Stack direction="row" spacing={2} alignItems="center">
          <Pagination
            color="primary"
            count={totalPages}
            page={(page ?? 0) + 1}
            onChange={(_e, p) => setPage(p - 1)}
            siblingCount={1}
            boundaryCount={2}
            size="small"
          />
          <TextField
            size="small"
            label="Page size"
            type="number"
            value={pageSizeInput}
            onChange={e => setPageSizeInput(e.target.value)}
            onBlur={handlePageSizeInput}
            onKeyDown={e => {
              if (e.key === 'Enter') handlePageSizeInput();
            }}
            slotProps={{
              htmlInput: { min: 1, max: 1000 },
              input: { endAdornment: <InputAdornment position="end">rows</InputAdornment> },
            }}
          />
          <Box>total rows: {totalRows}</Box>
        </Stack>
        <HeaderMenuButton
          menuHeaderAnchor={menuHeaderAnchor}
          handleMenuHeaderOpen={handleMenuHeaderOpen}
        />
      </Stack>
      <TableContainer
        component={Paper}
        sx={{ height: 'calc(100vh - 186px)', width: '100%', overflow: 'auto' }}>
        <Table stickyHeader size="small" aria-label="history table">
          <TableHead>
            <TableRow>
              <HistoryTableCell
                label="Connection ID"
                column="connectionId"
                sorting={sorting}
                filters={filters}
                keys={['id']}
                onSortingChange={setSorting}
              />
              {!regularUser ? (
                <HistoryTableCell
                  label="User"
                  column="userName"
                  sorting={sorting}
                  filters={filters}
                  keys={['un']}
                  onSortingChange={setSorting}
                />
              ) : null}
              <HistoryTableCell
                label="Client Host"
                column="clientHost"
                sorting={sorting}
                filters={filters}
                keys={['ch']}
                onSortingChange={setSorting}
              />
              <HistoryTableCell
                label="Target Host"
                column="targetHost"
                sorting={sorting}
                filters={filters}
                keys={['th']}
                onSortingChange={setSorting}
              />
              <HistoryTableCell
                label="Target Port"
                column="targetPort"
                sorting={sorting}
                filters={filters}
                keys={['tp']}
                onSortingChange={setSorting}
              />
              <HistoryTableCell
                label="Opened"
                column="openedAt"
                sorting={sorting}
                filters={filters}
                keys={['oa', 'ob']}
                onSortingChange={setSorting}
              />
              <HistoryTableCell
                label="Closed"
                column="closedAt"
                sorting={sorting}
                filters={filters}
                keys={['ca', 'cb']}
                onSortingChange={setSorting}
              />
              <HistoryTableCell
                label="Read"
                column="bytesRead"
                sorting={sorting}
                filters={filters}
                onSortingChange={setSorting}
              />
              <HistoryTableCell
                label="Written"
                column="bytesWritten"
                sorting={sorting}
                filters={filters}
                onSortingChange={setSorting}
              />
            </TableRow>
          </TableHead>
          <TableBody>
            {!!error || data.length === 0 ? (
              <TableRow>
                <TableCell colSpan={!regularUser ? 9 : 8}>
                  {error ? (
                    <Alert severity="error">{error}</Alert>
                  ) : (
                    <Alert severity="info">No history records found.</Alert>
                  )}
                </TableCell>
              </TableRow>
            ) : (
              data.map(c => (
                <TableRow key={c.id} hover>
                  <TableCell sx={{ paddingY: '13px', fontSizeAdjust: '0.5' }}>
                    <pre style={{ margin: 0 }}>{c.id}</pre>
                  </TableCell>
                  {!regularUser ? <TableCell>{c.user?.username ?? ''}</TableCell> : null}
                  <TableCell>{c.clientHost ?? ''}</TableCell>
                  <TableCell>{c.targetHost ?? ''}</TableCell>
                  <TableCell>{c.targetPort ?? ''}</TableCell>
                  <TableCell>{c.openedAt ? new Date(c.openedAt).toLocaleString() : ''}</TableCell>
                  <TableCell>{c.closedAt ? new Date(c.closedAt).toLocaleString() : ''}</TableCell>
                  <TableCell>{formatBytes(c.bytesRead ?? 0)}</TableCell>
                  <TableCell>{formatBytes(c.bytesWritten ?? 0)}</TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </TableContainer>

      <Menu
        id="history-header-menu"
        anchorEl={menuHeaderAnchor}
        open={!!menuHeaderAnchor}
        onClose={handleMenuHeaderClose}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        keepMounted>
        <MenuItem onClick={handleRefresh}>
          <ListItemIcon>
            <RefreshIcon fontSize="small" />
          </ListItemIcon>
          Refresh
        </MenuItem>
        <MenuItem onClick={handleOpenFilters}>
          <ListItemIcon>
            <FunnelIcon fontSize="small" />
          </ListItemIcon>
          Filters
        </MenuItem>
      </Menu>

      <HistoryFiltersDialog
        open={filtersOpen}
        regularUser={regularUser}
        value={filters}
        onApply={handleFiltersApply}
        onClose={onFiltersClose}
      />
    </>
  );
}

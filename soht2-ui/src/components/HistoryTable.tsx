/* SOHT2 © Licensed under MIT 2025. */
import { type MouseEvent, useCallback, useEffect, useMemo, useState } from 'react';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableSortLabel from '@mui/material/TableSortLabel';
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
  type Soht2Connection,
} from '../api/soht2Api';
import { formatBytes } from '../api/functions';
import { useDebounce } from '../hooks';
import HistoryFiltersDialog, { type HistoryFilters } from './HistoryFiltersDialog';

// Server field names for sort
export type HistorySortColumn =
  | 'connectionId'
  | 'userName'
  | 'clientHost'
  | 'targetHost'
  | 'targetPort'
  | 'openedAt'
  | 'closedAt'
  | 'bytesRead'
  | 'bytesWritten';
export type HistorySortDir = 'asc' | 'desc';
export type HistorySorting = { column: HistorySortColumn | null; direction: HistorySortDir | null };
export type HistoryNavigation = HistoryFilters & { sort?: string[]; pg?: number; sz?: number };

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
  const [sorting, setSorting] = useState<HistorySorting>({
    column: navColumn as HistorySortColumn,
    direction: navDir as HistorySortDir,
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

  useEffect(() => {
    void load();
  }, [load]);

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

  const toggleSort = useCallback((column: Exclude<HistorySortColumn, never>) => {
    setSorting(prev => {
      let next: HistorySorting = { column, direction: 'asc' };
      if (prev.column !== column) {
        next = { column, direction: 'asc' };
      } else if (prev.direction === 'asc') {
        next = { column, direction: 'desc' };
      } else if (prev.direction === 'desc') {
        next = { column: null, direction: null };
      }
      return next;
    });
  }, []);

  const totalRows = pageData?.totalItems ?? 0;
  const totalPages = pageData?.totalPages ?? 0;
  const data: Soht2Connection[] = pageData?.data ?? [];

  const sortDir = sorting.direction ?? false;
  const dir = sorting.direction ?? 'asc';

  const hasFilter = (keys: (keyof HistoryFilters)[]): boolean => {
    return keys.some(k => {
      const v = (filters as Record<string, unknown>)[k];
      if (Array.isArray(v)) return v.length > 0;
      return v !== undefined && v !== null && v !== '';
    });
  };

  if (loading) {
    return (
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
        <CircularProgress />
      </Box>
    );
  }

  if (error) {
    return (
      <Box sx={{ p: 2 }}>
        <Alert severity="error">{error}</Alert>
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
            count={totalPages ?? 1}
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
              <TableCell
                sortDirection={sorting.column === 'connectionId' ? sortDir : false}
                sx={{ paddingY: '10px' }}>
                <TableSortLabel
                  active={sorting.column === 'connectionId'}
                  direction={dir}
                  onClick={() => toggleSort('connectionId')}>
                  {hasFilter(['id']) && <FunnelIcon fontSize="inherit" sx={{ mr: 0.5 }} />}
                  <b>Connection ID</b>
                </TableSortLabel>
              </TableCell>
              {!regularUser ? (
                <TableCell sortDirection={sorting.column === 'userName' ? sortDir : false}>
                  <TableSortLabel
                    active={sorting.column === 'userName'}
                    direction={dir}
                    onClick={() => toggleSort('userName')}>
                    {hasFilter(['un']) && <FunnelIcon fontSize="inherit" sx={{ mr: 0.5 }} />}
                    <b>User</b>
                  </TableSortLabel>
                </TableCell>
              ) : null}
              <TableCell sortDirection={sorting.column === 'clientHost' ? sortDir : false}>
                <TableSortLabel
                  active={sorting.column === 'clientHost'}
                  direction={dir}
                  onClick={() => toggleSort('clientHost')}>
                  {hasFilter(['ch']) && <FunnelIcon fontSize="inherit" sx={{ mr: 0.5 }} />}
                  <b>Client Host</b>
                </TableSortLabel>
              </TableCell>
              <TableCell sortDirection={sorting.column === 'targetHost' ? sortDir : false}>
                <TableSortLabel
                  active={sorting.column === 'targetHost'}
                  direction={dir}
                  onClick={() => toggleSort('targetHost')}>
                  {hasFilter(['th']) && <FunnelIcon fontSize="inherit" sx={{ mr: 0.5 }} />}
                  <b>Target Host</b>
                </TableSortLabel>
              </TableCell>
              <TableCell sortDirection={sorting.column === 'targetPort' ? sortDir : false}>
                <TableSortLabel
                  active={sorting.column === 'targetPort'}
                  direction={dir}
                  onClick={() => toggleSort('targetPort')}>
                  {hasFilter(['tp']) && <FunnelIcon fontSize="inherit" sx={{ mr: 0.5 }} />}
                  <b>Target Port</b>
                </TableSortLabel>
              </TableCell>
              <TableCell sortDirection={sorting.column === 'openedAt' ? sortDir : false}>
                <TableSortLabel
                  active={sorting.column === 'openedAt'}
                  direction={dir}
                  onClick={() => toggleSort('openedAt')}>
                  {hasFilter(['oa', 'ob']) && <FunnelIcon fontSize="inherit" sx={{ mr: 0.5 }} />}
                  <b>Opened</b>
                </TableSortLabel>
              </TableCell>
              <TableCell sortDirection={sorting.column === 'closedAt' ? sortDir : false}>
                <TableSortLabel
                  active={sorting.column === 'closedAt'}
                  direction={dir}
                  onClick={() => toggleSort('closedAt')}>
                  {hasFilter(['ca', 'cb']) && <FunnelIcon fontSize="inherit" sx={{ mr: 0.5 }} />}
                  <b>Closed</b>
                </TableSortLabel>
              </TableCell>
              <TableCell sortDirection={sorting.column === 'bytesRead' ? sortDir : false}>
                <TableSortLabel
                  active={sorting.column === 'bytesRead'}
                  direction={dir}
                  onClick={() => toggleSort('bytesRead')}>
                  <b>Read</b>
                </TableSortLabel>
              </TableCell>
              <TableCell sortDirection={sorting.column === 'bytesWritten' ? sortDir : false}>
                <TableSortLabel
                  active={sorting.column === 'bytesWritten'}
                  direction={dir}
                  onClick={() => toggleSort('bytesWritten')}>
                  <b>Written</b>
                </TableSortLabel>
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data.length === 0 ? (
              <TableRow>
                <TableCell colSpan={7}>
                  <Alert severity="info">No history records found</Alert>
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

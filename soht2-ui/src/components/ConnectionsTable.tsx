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
import IconButton from '@mui/material/IconButton';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import CircularProgress from '@mui/material/CircularProgress';
import Box from '@mui/material/Box';
import Alert from '@mui/material/Alert';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import ListItemIcon from '@mui/material/ListItemIcon';
import RefreshIcon from '@mui/icons-material/Refresh';
import ToggleOnIcon from '@mui/icons-material/ToggleOn';
import ToggleOffIcon from '@mui/icons-material/ToggleOff';
import LinkOffIcon from '@mui/icons-material/LinkOff';
import Stack from '@mui/material/Stack';
import {
  type ApiError,
  ConnectionApi,
  type ISODateTime,
  type Soht2Connection,
} from '../api/soht2Api';
import { ConnectionChangedEvent } from '../api/appEvents';
import HeaderMenuButton from '../controls/HeaderMenuButton';
import ConnectionCloseDialog from './ConnectionCloseDialog';
import useInterval from '../hooks/useInterval';
import useEventListener from '../hooks/useEventListener';

type SortColumn =
  | 'id'
  | 'username'
  | 'clientHost'
  | 'targetHost'
  | 'targetPort'
  | 'openedAt'
  | null;
export type ConnectionsSorting = { column: SortColumn; direction: 'asc' | 'desc' | null };

function HeaderMenu({
  menuHeaderAnchor,
  autoRefresh,
  handleMenuHeaderClose,
  handleManualRefresh,
  handleToggleAutoRefresh,
}: Readonly<{
  menuHeaderAnchor: HTMLElement | null;
  autoRefresh: boolean;
  handleMenuHeaderClose: () => void;
  handleManualRefresh: () => void;
  handleToggleAutoRefresh: () => void;
}>) {
  return (
    <Menu
      id="connections-header-menu"
      anchorEl={menuHeaderAnchor}
      open={!!menuHeaderAnchor}
      onClose={handleMenuHeaderClose}
      anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      transformOrigin={{ vertical: 'top', horizontal: 'right' }}
      keepMounted>
      <MenuItem onClick={handleManualRefresh}>
        <ListItemIcon>
          <RefreshIcon fontSize="small" />
        </ListItemIcon>
        Manual Refresh
      </MenuItem>
      <MenuItem onClick={handleToggleAutoRefresh}>
        <ListItemIcon>
          {autoRefresh ? <ToggleOffIcon fontSize="small" /> : <ToggleOnIcon fontSize="small" />}
        </ListItemIcon>
        {`Automatic Refresh ${autoRefresh ? 'Off' : 'On'}`}
      </MenuItem>
    </Menu>
  );
}

export default function ConnectionsTable({
  initSorting,
  onSortingChange,
  initAutoRefresh = false,
  onAutoRefreshChange,
}: Readonly<{
  initSorting?: ConnectionsSorting;
  onSortingChange?: (s: ConnectionsSorting) => void;
  initAutoRefresh?: boolean;
  onAutoRefreshChange?: (r: boolean) => void;
}>) {
  const [connections, setConnections] = useState<Soht2Connection[] | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const [menuHeaderAnchor, setMenuHeaderAnchor] = useState<HTMLElement | null>(null);
  const [menuRowAnchor, setMenuRowAnchor] = useState<HTMLElement | null>(null);
  const [selectedConnection, setSelectedConnection] = useState<Soht2Connection | null>(null);

  const [sorting, setSorting] = useState<ConnectionsSorting>(
    initSorting ?? { column: null, direction: null }
  );

  const [autoRefresh, setAutoRefresh] = useState<boolean>(initAutoRefresh);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await ConnectionApi.list();
      setConnections(data ?? []);
    } catch (e) {
      const apiError = e as ApiError;
      setError(apiError.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => void load(), [load]);

  useEventListener(ConnectionChangedEvent.TYPE, () => void load());

  const [setRefresh, cancelRefresh] = useInterval(() => {
    if (menuHeaderAnchor) return;
    void load();
  }, 5000);

  useEffect(() => {
    if (autoRefresh) setRefresh();
    return cancelRefresh;
  }, [autoRefresh, menuHeaderAnchor, load, setRefresh, cancelRefresh]);

  const handleMenuHeaderOpen = useCallback((e: MouseEvent<HTMLElement>) => {
    setMenuHeaderAnchor(e.currentTarget);
  }, []);
  const handleMenuHeaderClose = useCallback(() => setMenuHeaderAnchor(null), []);

  const handleManualRefresh = useCallback(() => {
    handleMenuHeaderClose();
    void load();
  }, [handleMenuHeaderClose, load]);

  const handleToggleAutoRefresh = useCallback(() => {
    setAutoRefresh(prev => {
      const newAutoRefresh = !prev;
      if (onAutoRefreshChange) onAutoRefreshChange(newAutoRefresh);
      return newAutoRefresh;
    });
    handleMenuHeaderClose();
  }, [onAutoRefreshChange, handleMenuHeaderClose]);

  const handleMenuRowOpen = useCallback((e: MouseEvent<HTMLElement>, conn: Soht2Connection) => {
    setSelectedConnection(conn);
    setMenuRowAnchor(e.currentTarget);
  }, []);
  const handleMenuRowClose = useCallback(() => setMenuRowAnchor(null), []);

  const [closeConnectionOpen, setCloseConnectionOpen] = useState(false);
  const handleCloseConnectionOpen = useCallback(() => {
    handleMenuRowClose();
    setCloseConnectionOpen(true);
  }, [handleMenuRowClose]);
  const handleCloseConnectionClose = useCallback(() => setCloseConnectionOpen(false), []);

  const toggleSort = useCallback(
    (column: Exclude<SortColumn, null>) => {
      const nextSorting: ConnectionsSorting = { column, direction: 'asc' };
      if (sorting.column !== column) {
        nextSorting.column = column;
      } else if (sorting.direction === 'asc') {
        nextSorting.direction = 'desc';
      } else if (sorting.direction === 'desc') {
        nextSorting.column = null;
        nextSorting.direction = null;
      }
      setSorting(nextSorting);
      if (onSortingChange) onSortingChange(nextSorting);
    },
    [onSortingChange, sorting.column, sorting.direction]
  );

  const sortedConnections = useMemo(() => {
    const list = connections ?? [];
    if (!sorting.column || !sorting.direction) return list;
    const arr = [...list];
    type Col = Exclude<SortColumn, null>;
    const cmp = (a: Soht2Connection, b: Soht2Connection): number => {
      const asTime = (t?: ISODateTime | null) => (t ? new Date(t).getTime() : 0);
      const asNumber = (n?: number | null) => Number(n ?? 0);
      const asString = (c: Soht2Connection) => {
        const col = sorting.column as Col;
        return (col === 'username' ? (c.user?.username ?? '') : (c[col] ?? ''))
          .toString()
          .toLowerCase();
      };
      if (sorting.column === 'openedAt') return asTime(a.openedAt) - asTime(b.openedAt);
      if (sorting.column === 'targetPort') return asNumber(a.targetPort) - asNumber(b.targetPort);
      return asString(a).localeCompare(asString(b));
    };
    arr.sort((a, b) => (sorting.direction === 'asc' ? cmp(a, b) : -cmp(a, b)));
    return arr;
  }, [connections, sorting.column, sorting.direction]);

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

  if (sortedConnections.length === 0) {
    return (
      <Stack direction="row" spacing={2} justifyContent="space-between">
        <Box sx={{ p: 2 }} width="100%">
          <Alert severity="info">No active connections</Alert>
        </Box>
        <Box sx={{ py: 3 }}>
          <HeaderMenuButton
            menuHeaderAnchor={menuHeaderAnchor}
            handleMenuHeaderOpen={handleMenuHeaderOpen}
          />
          <HeaderMenu
            menuHeaderAnchor={menuHeaderAnchor}
            autoRefresh={autoRefresh}
            handleMenuHeaderClose={handleMenuHeaderClose}
            handleManualRefresh={handleManualRefresh}
            handleToggleAutoRefresh={handleToggleAutoRefresh}
          />
        </Box>
      </Stack>
    );
  }

  const sortDir = sorting.direction ?? false;
  const dir = sorting.direction ?? 'asc';

  return (
    <>
      <TableContainer component={Paper} sx={{ height: '100%', width: '100%', overflow: 'auto' }}>
        <Table stickyHeader size="small" aria-label="connections table">
          <TableHead>
            <TableRow>
              <TableCell sortDirection={sorting.column === 'id' ? sortDir : false}>
                <TableSortLabel
                  active={sorting.column === 'id'}
                  direction={dir}
                  onClick={() => toggleSort('id')}>
                  <b>Connection ID</b>
                </TableSortLabel>
              </TableCell>
              <TableCell sortDirection={sorting.column === 'username' ? sortDir : false}>
                <TableSortLabel
                  active={sorting.column === 'username'}
                  direction={dir}
                  onClick={() => toggleSort('username')}>
                  <b>User</b>
                </TableSortLabel>
              </TableCell>
              <TableCell sortDirection={sorting.column === 'clientHost' ? sortDir : false}>
                <TableSortLabel
                  active={sorting.column === 'clientHost'}
                  direction={dir}
                  onClick={() => toggleSort('clientHost')}>
                  <b>Client Host</b>
                </TableSortLabel>
              </TableCell>
              <TableCell sortDirection={sorting.column === 'targetHost' ? sortDir : false}>
                <TableSortLabel
                  active={sorting.column === 'targetHost'}
                  direction={dir}
                  onClick={() => toggleSort('targetHost')}>
                  <b>Target Host</b>
                </TableSortLabel>
              </TableCell>
              <TableCell sortDirection={sorting.column === 'targetPort' ? sortDir : false}>
                <TableSortLabel
                  active={sorting.column === 'targetPort'}
                  direction={dir}
                  onClick={() => toggleSort('targetPort')}>
                  <b>Target Port</b>
                </TableSortLabel>
              </TableCell>
              <TableCell sortDirection={sorting.column === 'openedAt' ? sortDir : false}>
                <TableSortLabel
                  active={sorting.column === 'openedAt'}
                  direction={dir}
                  onClick={() => toggleSort('openedAt')}>
                  <b>Opened</b>
                </TableSortLabel>
              </TableCell>
              <TableCell align="right">
                <HeaderMenuButton
                  menuHeaderAnchor={menuHeaderAnchor}
                  handleMenuHeaderOpen={handleMenuHeaderOpen}
                />
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {sortedConnections.map(c => {
              return (
                <TableRow key={c.id} hover>
                  <TableCell sx={{ paddingY: '13px', fontSizeAdjust: '0.5' }}>
                    <pre style={{ margin: 0 }}>{c.id}</pre>
                  </TableCell>
                  <TableCell>{c.user?.username || ''}</TableCell>
                  <TableCell>{c.clientHost || ''}</TableCell>
                  <TableCell>{c.targetHost || ''}</TableCell>
                  <TableCell>{c.targetPort ?? ''}</TableCell>
                  <TableCell>{c.openedAt ? new Date(c.openedAt).toLocaleString() : ''}</TableCell>
                  <TableCell align="right">
                    <IconButton
                      size="small"
                      aria-label={`actions-${c.id}`}
                      aria-controls={menuRowAnchor ? 'connection-row-menu' : undefined}
                      aria-haspopup="true"
                      onClick={e => handleMenuRowOpen(e, c)}>
                      <MoreVertIcon />
                    </IconButton>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>

      <HeaderMenu
        menuHeaderAnchor={menuHeaderAnchor}
        autoRefresh={autoRefresh}
        handleMenuHeaderClose={handleMenuHeaderClose}
        handleManualRefresh={handleManualRefresh}
        handleToggleAutoRefresh={handleToggleAutoRefresh}
      />

      <Menu
        id="connection-row-menu"
        anchorEl={menuRowAnchor}
        open={!!menuRowAnchor}
        onClose={handleMenuRowClose}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        keepMounted>
        <MenuItem onClick={handleCloseConnectionOpen} disabled={!selectedConnection}>
          <ListItemIcon>
            <LinkOffIcon fontSize="small" />
          </ListItemIcon>
          Close Connection
        </MenuItem>
      </Menu>

      <ConnectionCloseDialog
        open={closeConnectionOpen}
        connectionId={selectedConnection?.id}
        onClose={handleCloseConnectionClose}
      />
    </>
  );
}

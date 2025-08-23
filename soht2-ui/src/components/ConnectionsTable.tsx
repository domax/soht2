/* SOHT2 © Licensed under MIT 2025. */
import { type MouseEvent, useCallback, useEffect, useMemo, useState } from 'react';
import Paper from '@mui/material/Paper';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
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
import {
  type ApiError,
  ConnectionApi,
  type Soht2Connection,
  type TableSorting,
} from '../api/soht2Api';
import { compareNumbers, compareStrings, compareTimes, formatBytes } from '../api/functions';
import { ConnectionChangedEvent } from '../api/appEvents';
import { useEventListener, useInterval } from '../hooks';
import HeaderMenuButton from '../controls/HeaderMenuButton';
import ConnectionCloseDialog from './ConnectionCloseDialog';
import TableHeaderCell from './TableHeaderCell';

type ConnectionSortColumn =
  | 'id'
  | 'username'
  | 'clientHost'
  | 'targetHost'
  | 'targetPort'
  | 'openedAt'
  | 'bytesRead'
  | 'bytesWritten';
export type ConnectionsSorting = TableSorting<ConnectionSortColumn>;

export default function ConnectionsTable({
  initSorting,
  initAutoRefresh = false,
  onSortingChange,
  onAutoRefreshChange,
}: Readonly<{
  initSorting?: ConnectionsSorting;
  initAutoRefresh?: boolean;
  onSortingChange: (s: ConnectionsSorting) => void;
  onAutoRefreshChange: (r: boolean) => void;
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
      onAutoRefreshChange(newAutoRefresh);
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

  useEffect(() => onSortingChange(sorting), [onSortingChange, sorting]);

  const sortedConnections = useMemo(() => {
    const list = connections ?? [];
    if (!sorting.column || !sorting.direction) return list;
    const arr = [...list];
    const cmp = (a: Soht2Connection, b: Soht2Connection): number => {
      switch (sorting.column) {
        case null:
          return 0;
        case 'openedAt':
          return compareTimes(a[sorting.column], b[sorting.column]);
        case 'targetPort':
        case 'bytesRead':
        case 'bytesWritten':
          return compareNumbers(a[sorting.column], b[sorting.column]);
        case 'username':
          return compareStrings(a.user?.username, b.user?.username);
        default:
          return compareStrings(a[sorting.column], b[sorting.column]);
      }
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

  return (
    <>
      <TableContainer component={Paper} sx={{ height: '100%', width: '100%', overflow: 'auto' }}>
        <Table stickyHeader size="small" aria-label="connections table">
          <TableHead>
            <TableRow>
              <TableHeaderCell
                label="Connection ID"
                column="id"
                sorting={sorting}
                onSortingChange={setSorting}
              />
              <TableHeaderCell
                label="User"
                column="username"
                sorting={sorting}
                onSortingChange={setSorting}
              />
              <TableHeaderCell
                label="Client Host"
                column="clientHost"
                sorting={sorting}
                onSortingChange={setSorting}
              />
              <TableHeaderCell
                label="Target Host"
                column="targetHost"
                sorting={sorting}
                onSortingChange={setSorting}
              />
              <TableHeaderCell
                label="Target Port"
                column="targetPort"
                sorting={sorting}
                onSortingChange={setSorting}
              />
              <TableHeaderCell
                label="Opened"
                column="openedAt"
                sorting={sorting}
                onSortingChange={setSorting}
              />
              <TableHeaderCell
                label="Read"
                column="bytesRead"
                sorting={sorting}
                onSortingChange={setSorting}
              />
              <TableHeaderCell
                label="Written"
                column="bytesWritten"
                sorting={sorting}
                onSortingChange={setSorting}
              />
              <TableCell align="right">
                <HeaderMenuButton
                  menuHeaderAnchor={menuHeaderAnchor}
                  handleMenuHeaderOpen={handleMenuHeaderOpen}
                />
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {!!error || sortedConnections.length === 0 ? (
              <TableRow>
                <TableCell colSpan={9}>
                  {error ? (
                    <Alert severity="error">{error}</Alert>
                  ) : (
                    <Alert severity="info">No active connections.</Alert>
                  )}
                </TableCell>
              </TableRow>
            ) : (
              sortedConnections.map(c => {
                return (
                  <TableRow key={c.id} hover>
                    <TableCell sx={{ paddingY: '13px', fontSizeAdjust: '0.5' }}>
                      <pre style={{ margin: 0 }}>{c.id}</pre>
                    </TableCell>
                    <TableCell>{c.user?.username ?? ''}</TableCell>
                    <TableCell>{c.clientHost ?? ''}</TableCell>
                    <TableCell>{c.targetHost ?? ''}</TableCell>
                    <TableCell>{c.targetPort ?? ''}</TableCell>
                    <TableCell>{c.openedAt ? new Date(c.openedAt).toLocaleString() : ''}</TableCell>
                    <TableCell>{formatBytes(c.bytesRead ?? 0)}</TableCell>
                    <TableCell>{formatBytes(c.bytesWritten ?? 0)}</TableCell>
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
              })
            )}
          </TableBody>
        </Table>
      </TableContainer>

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
          Refresh
        </MenuItem>
        <MenuItem onClick={handleToggleAutoRefresh}>
          <ListItemIcon>
            {autoRefresh ? <ToggleOffIcon fontSize="small" /> : <ToggleOnIcon fontSize="small" />}
          </ListItemIcon>
          {`Automatic Refresh ${autoRefresh ? 'Off' : 'On'}`}
        </MenuItem>
      </Menu>

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

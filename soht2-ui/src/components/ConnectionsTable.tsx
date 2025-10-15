/* SOHT2 © Licensed under MIT 2025. */
import { type MouseEvent, useCallback, useEffect, useMemo, useState } from 'react';
import Paper from '@mui/material/Paper';
import IconButton from '@mui/material/IconButton';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import Box from '@mui/material/Box';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import ListItemIcon from '@mui/material/ListItemIcon';
import RefreshIcon from '@mui/icons-material/Refresh';
import ToggleOnIcon from '@mui/icons-material/ToggleOn';
import ToggleOffIcon from '@mui/icons-material/ToggleOff';
import LinkOffIcon from '@mui/icons-material/LinkOff';
import {
  DataGrid,
  getGridDateOperators,
  type GridColDef,
  type GridColumnVisibilityModel,
  type GridFilterModel,
  type GridSortModel,
} from '@mui/x-data-grid';
import {
  type ApiError,
  ConnectionApi,
  type Soht2Connection,
  type TableSorting,
} from '../api/soht2Api';
import { getDataGridStyle, formatBytes, formatDateTime } from '../api/functions';
import { ConnectionChangedEvent, dispatchAppErrorEvent } from '../api/appEvents';
import { useEventListener, useInterval } from '../hooks';
import HeaderMenuButton from '../controls/HeaderMenuButton';
import ConnectionCloseDialog from './ConnectionCloseDialog';
import { useTheme } from '@mui/material/styles';
import DateTimeGridFilter from '../controls/DateTimeGridFilter';
import { NoRowsOverlay, LoadingOverlay } from '../controls/dataGridOverlays';

type ConnectionVisibilityColumn =
  | 'username'
  | 'clientHost'
  | 'targetHost'
  | 'targetPort'
  | 'openedAt'
  | 'bytesRead'
  | 'bytesWritten';
type ConnectionSortColumn = 'id' | ConnectionVisibilityColumn;
export type ConnectionsSorting = TableSorting<ConnectionSortColumn>;

export type ConnectionVisibility = { [K in ConnectionVisibilityColumn]?: boolean };

export type ConnectionFilter = {
  field: ConnectionSortColumn;
  operator: string;
  value: string | number | null;
};

export type ConnectionSettings = {
  sorting: ConnectionsSorting;
  visibility: ConnectionVisibility;
  filters: ConnectionFilter[];
  autoRefresh: boolean;
};

export default function ConnectionsTable({
  initSettings,
  onSettingsChange,
}: Readonly<{
  initSettings?: ConnectionSettings;
  onSettingsChange?: (s: ConnectionSettings) => void;
}>) {
  const theme = useTheme();

  const [connections, setConnections] = useState([] as Soht2Connection[]);
  const [loading, setLoading] = useState(false);
  const [menuHeaderAnchor, setMenuHeaderAnchor] = useState<HTMLElement | null>(null);
  const [menuRowAnchor, setMenuRowAnchor] = useState<HTMLElement | null>(null);
  const [selectedConnection, setSelectedConnection] = useState<Soht2Connection | null>(null);
  const [sorting, setSorting] = useState(
    initSettings?.sorting ?? { column: null, direction: null }
  );
  const [visibility, setVisibility] = useState(initSettings?.visibility ?? {});
  const [filters, setFilters] = useState(initSettings?.filters ?? []);

  const [autoRefresh, setAutoRefresh] = useState(initSettings?.autoRefresh ?? false);
  const [bypassCounter, setBypassCounter] = useState(0);

  const [closeConnectionOpen, setCloseConnectionOpen] = useState(false);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      const data = await ConnectionApi.list();
      setConnections(data ?? []);
    } catch (e) {
      dispatchAppErrorEvent(e as ApiError);
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => void load(), [load]);
  useEventListener(ConnectionChangedEvent.TYPE, () => void load());

  const [setRefresh, cancelRefresh] = useInterval(5000, () => void load());

  useEffect(() => {
    if (autoRefresh && !bypassCounter) setRefresh();
    return cancelRefresh;
  }, [autoRefresh, setRefresh, cancelRefresh, bypassCounter]);

  const handleBypassCounterOpen = useCallback(() => setBypassCounter(v => v + 1), []);
  const handleBypassCounterClose = useCallback(
    () => setBypassCounter(v => (v > 0 ? v - 1 : 0)),
    []
  );

  const handleMenuHeaderOpen = useCallback(
    (e: MouseEvent<HTMLElement>) => {
      handleBypassCounterOpen();
      setMenuHeaderAnchor(e.currentTarget);
    },
    [handleBypassCounterOpen]
  );
  const handleMenuHeaderClose = useCallback(() => {
    setMenuHeaderAnchor(null);
    handleBypassCounterClose();
  }, [handleBypassCounterClose]);
  const handleManualRefresh = useCallback(() => {
    handleMenuHeaderClose();
    void load();
  }, [handleMenuHeaderClose, load]);
  const handleToggleAutoRefresh = useCallback(() => {
    setAutoRefresh(prev => !prev);
    handleMenuHeaderClose();
  }, [handleMenuHeaderClose]);

  const handleMenuRowOpen = useCallback((e: MouseEvent<HTMLElement>, conn: Soht2Connection) => {
    setSelectedConnection(conn);
    setMenuRowAnchor(e.currentTarget);
  }, []);
  const handleMenuRowClose = useCallback(() => setMenuRowAnchor(null), []);
  const handleCloseConnectionOpen = useCallback(() => {
    handleMenuRowClose();
    setCloseConnectionOpen(true);
  }, [handleMenuRowClose]);
  const handleCloseConnectionClose = useCallback(() => setCloseConnectionOpen(false), []);

  useEffect(() => {
    if (onSettingsChange) onSettingsChange({ sorting, visibility, filters, autoRefresh });
  }, [autoRefresh, filters, onSettingsChange, sorting, visibility]);

  const sortModel: GridSortModel = useMemo(() => {
    if (!sorting.column || !sorting.direction) return [];
    return [{ field: sorting.column, sort: sorting.direction }];
  }, [sorting]);
  const handleSortModelChange = useCallback((model: GridSortModel) => {
    if (!model || model.length === 0) {
      setSorting({ column: null, direction: null });
      return;
    }
    const m = model[0];
    setSorting({
      column: m.field as ConnectionSortColumn,
      direction: (m.sort ?? null) as ConnectionsSorting['direction'],
    });
  }, []);
  const handleColumnVisibilityModelChange = useCallback(
    (m: GridColumnVisibilityModel) => setVisibility(m as ConnectionVisibility),
    []
  );
  const handleFilterModelChange = useCallback((m: GridFilterModel) => {
    setFilters(
      (m?.items ?? []).map<ConnectionFilter>(({ field, operator, value }) => ({
        field: field as ConnectionSortColumn,
        operator,
        value,
      }))
    );
  }, []);

  const rows = useMemo(
    () => connections.map(c => ({ ...c, username: c.user?.username ?? '' })),
    [connections]
  );

  const columns: GridColDef[] = useMemo(
    () => [
      {
        field: 'id',
        headerName: 'Connection ID',
        flex: 1.5,
        minWidth: 330,
        hideable: false,
        renderCell: ({ value }) => <pre style={{ margin: 0 }}>{value ?? ''}</pre>,
      },
      { field: 'username', headerName: 'User', flex: 0.7, minWidth: 120 },
      { field: 'clientHost', headerName: 'Client Host', flex: 0.7, minWidth: 120 },
      { field: 'targetHost', headerName: 'Target Host', flex: 0.7, minWidth: 120 },
      {
        field: 'targetPort',
        type: 'number',
        headerName: 'Target Port',
        flex: 0.3,
        minWidth: 100,
        align: 'right',
        valueGetter: Number,
      },
      {
        field: 'openedAt',
        type: 'dateTime',
        headerName: 'Opened',
        flex: 0.5,
        minWidth: 150,
        valueGetter: value => new Date(value),
        renderCell: ({ value }) => (value ? formatDateTime(value) : ''),
        filterOperators: getGridDateOperators(true).map(operator => ({
          ...operator,
          InputComponent: operator.InputComponent ? DateTimeGridFilter : undefined,
        })),
      },
      {
        field: 'bytesRead',
        type: 'number',
        headerName: 'Read',
        flex: 0.5,
        minWidth: 120,
        valueGetter: Number,
        renderCell: ({ value }) => formatBytes(value ?? 0),
      },
      {
        field: 'bytesWritten',
        type: 'number',
        headerName: 'Written',
        flex: 0.5,
        minWidth: 120,
        valueGetter: Number,
        renderCell: ({ value }) => formatBytes(value ?? 0),
      },
      {
        field: '__rowActions',
        headerName: 'Row Actions',
        width: 60,
        align: 'right',
        headerAlign: 'right',
        sortable: false,
        filterable: false,
        disableColumnMenu: true,
        hideable: false,
        renderHeader: () => (
          <HeaderMenuButton
            menuHeaderAnchor={menuHeaderAnchor}
            handleMenuHeaderOpen={handleMenuHeaderOpen}
          />
        ),
        renderCell: ({ row }) => (
          <IconButton
            size="small"
            aria-label={`actions-${(row as Soht2Connection).id}`}
            aria-controls={menuRowAnchor ? 'user-row-menu' : undefined}
            aria-haspopup="true"
            onClick={e => handleMenuRowOpen(e, row as Soht2Connection)}>
            <MoreVertIcon />
          </IconButton>
        ),
      },
    ],
    [handleMenuHeaderOpen, handleMenuRowOpen, menuHeaderAnchor, menuRowAnchor]
  );

  const sx = useMemo(() => getDataGridStyle(theme), [theme]);

  return (
    <>
      <Paper sx={{ height: '100%', width: '100%', display: 'flex', flexDirection: 'column' }}>
        <Box sx={{ flex: 1 }}>
          <DataGrid
            columns={columns}
            rows={rows}
            initialState={{ filter: { filterModel: { items: filters } } }}
            getRowId={(row: Soht2Connection) => row.id}
            sortingMode="client"
            loading={loading}
            sortModel={sortModel}
            columnVisibilityModel={visibility}
            onSortModelChange={handleSortModelChange}
            onColumnVisibilityModelChange={handleColumnVisibilityModelChange}
            onFilterModelChange={handleFilterModelChange}
            onMenuOpen={handleBypassCounterOpen}
            onMenuClose={handleBypassCounterClose}
            onPreferencePanelOpen={handleBypassCounterOpen}
            onPreferencePanelClose={handleBypassCounterClose}
            disableRowSelectionOnClick
            hideFooter={true}
            paginationModel={{ page: 0, pageSize: rows.length }}
            slots={{ noRowsOverlay: NoRowsOverlay, loadingOverlay: LoadingOverlay }}
            slotProps={{
              noRowsOverlay: { message: 'No active connections available to display.' },
            }}
            sx={sx}
          />
        </Box>
      </Paper>

      <Menu
        id="connections-header-menu"
        anchorEl={menuHeaderAnchor}
        open={!!menuHeaderAnchor}
        onClose={handleMenuHeaderClose}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        keepMounted>
        <MenuItem onClick={handleManualRefresh} disabled={autoRefresh}>
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

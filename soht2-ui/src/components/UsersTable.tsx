/* SOHT2 © Licensed under MIT 2025. */
import { type MouseEvent, useCallback, useEffect, useMemo, useState } from 'react';
import Paper from '@mui/material/Paper';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import ListItemIcon from '@mui/material/ListItemIcon';
import IconButton from '@mui/material/IconButton';
import { useTheme } from '@mui/material/styles';
import MoreVertIcon from '@mui/icons-material/MoreVert';
import PersonAddAlt1Icon from '@mui/icons-material/PersonAddAlt1';
import ManageAccountsIcon from '@mui/icons-material/ManageAccounts';
import PersonRemoveAlt1Icon from '@mui/icons-material/PersonRemoveAlt1';
import RefreshIcon from '@mui/icons-material/Refresh';
import {
  DataGrid,
  getGridDateOperators,
  type GridColDef,
  type GridColumnVisibilityModel,
  type GridFilterModel,
  type GridSortModel,
} from '@mui/x-data-grid';
import { type ApiError, type Soht2User, type TableSorting, UserApi } from '../api/soht2Api';
import { formatDateTime, getDataGridStyle } from '../api/functions';
import { dispatchAppErrorEvent, UserChangedEvent } from '../api/appEvents';
import { useEventListener } from '../hooks';
import HeaderMenuButton from '../controls/HeaderMenuButton';
import NewUserDialog from './NewUserDialog';
import EditUserDialog from './EditUserDialog';
import DeleteUserDialog from './DeleteUserDialog';
import DateTimeGridFilter from '../controls/DateTimeGridFilter';
import { LoadingOverlay, NoRowsOverlay } from '../controls/dataGridOverlays';

type UserSortColumn = 'username' | 'role' | 'createdAt';
export type UsersSorting = TableSorting<UserSortColumn>;

type UserVisibilityColumn = 'role' | 'createdAt' | 'allowedTargets';
export type UsersVisibility = { [K in UserVisibilityColumn]?: boolean };

export type UserFilter = { field: UserSortColumn; operator: string; value: string | number | null };

export type UserSettings = {
  sorting: UsersSorting;
  visibility: UsersVisibility;
  filters: UserFilter[];
};

export default function UsersTable({
  initSettings,
  onSettingsChange,
}: Readonly<{ initSettings?: UserSettings; onSettingsChange?: (s: UserSettings) => void }>) {
  const theme = useTheme();

  const [users, setUsers] = useState<Soht2User[]>([]);
  const [loading, setLoading] = useState(false);
  const [menuHeaderAnchor, setMenuHeaderAnchor] = useState<HTMLElement | null>(null);
  const [menuRowAnchor, setMenuRowAnchor] = useState<HTMLElement | null>(null);
  const [selectedUser, setSelectedUser] = useState<Soht2User | null>(null);
  const [newUserOpen, setNewUserOpen] = useState(false);
  const [editUserOpen, setEditUserOpen] = useState(false);
  const [deleteUserOpen, setDeleteUserOpen] = useState(false);
  const [sorting, setSorting] = useState(
    initSettings?.sorting ?? { column: null, direction: null }
  );
  const [visibility, setVisibility] = useState(initSettings?.visibility ?? {});
  const [filters, setFilters] = useState(initSettings?.filters ?? []);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      const data = await UserApi.listUsers();
      setUsers(data ?? []);
    } catch (e) {
      dispatchAppErrorEvent(e as ApiError);
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => void load(), [load]);
  useEventListener(UserChangedEvent.TYPE, () => void load());

  const handleMenuHeaderOpen = useCallback(
    (e: MouseEvent<HTMLElement>) => setMenuHeaderAnchor(e.currentTarget),
    []
  );
  const handleMenuHeaderClose = useCallback(() => setMenuHeaderAnchor(null), []);
  const handleManualRefresh = useCallback(() => {
    handleMenuHeaderClose();
    void load();
  }, [handleMenuHeaderClose, load]);
  const handleNewUserOpen = useCallback(() => {
    handleMenuHeaderClose();
    setNewUserOpen(true);
  }, [handleMenuHeaderClose]);

  const handleMenuRowOpen = useCallback((e: MouseEvent<HTMLElement>, user: Soht2User) => {
    setSelectedUser(user);
    setMenuRowAnchor(e.currentTarget);
  }, []);
  const handleMenuRowClose = useCallback(() => setMenuRowAnchor(null), []);
  const handleEditUserOpen = useCallback(() => {
    handleMenuRowClose();
    setEditUserOpen(true);
  }, [handleMenuRowClose]);
  const handleDeleteUserOpen = useCallback(() => {
    handleMenuRowClose();
    setDeleteUserOpen(true);
  }, [handleMenuRowClose]);

  const handleNewUserClose = useCallback(() => setNewUserOpen(false), []);
  const handleEditUserClose = useCallback(() => setEditUserOpen(false), []);
  const handleDeleteUserClose = useCallback(() => setDeleteUserOpen(false), []);

  useEffect(() => {
    if (onSettingsChange) onSettingsChange({ sorting, visibility, filters });
  }, [filters, onSettingsChange, sorting, visibility]);

  const sortModel = useMemo(
    () =>
      sorting.column && sorting.direction
        ? ([{ field: sorting.column, sort: sorting.direction }] as GridSortModel)
        : [],
    [sorting]
  );
  const handleSortModelChange = useCallback((model: GridSortModel) => {
    const { field, sort } = model?.length > 0 ? model[0] : { field: null, sort: null };
    setSorting({
      column: field as UserSortColumn,
      direction: (sort ?? null) as UsersSorting['direction'],
    });
  }, []);
  const handleColumnVisibilityModelChange = useCallback(
    (m: GridColumnVisibilityModel) => setVisibility(m as UsersVisibility),
    []
  );
  const handleFilterModelChange = useCallback((m: GridFilterModel) => {
    setFilters(
      (m?.items ?? []).map<UserFilter>(({ field, operator, value }) => ({
        field: field as UserSortColumn,
        operator,
        value,
      }))
    );
  }, []);

  const rows = useMemo(() => users.map(u => ({ ...u, id: u.username })), [users]);

  const columns: GridColDef[] = useMemo(
    () => [
      { field: 'username', headerName: 'Name', flex: 1, minWidth: 150, hideable: false },
      {
        field: 'role',
        headerName: 'Role',
        flex: 0.7,
        minWidth: 120,
        renderCell: ({ value }) => String(value ?? ''),
      },
      {
        field: 'createdAt',
        type: 'dateTime',
        headerName: 'Created',
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
        field: 'allowedTargets',
        headerName: 'Allowed Targets',
        flex: 2,
        minWidth: 220,
        sortable: false,
        filterable: false,
        renderCell: ({ value }) => {
          const targets = (value as string[] | undefined) ?? [];
          return (
            <Box
              sx={{
                display: 'flex',
                flexWrap: 'nowrap',
                gap: 0.5,
                alignItems: 'center',
                paddingTop: 1.5,
              }}>
              {targets.map(t => (
                <Chip key={t} label={t} size="small" variant="outlined" />
              ))}
            </Box>
          );
        },
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
            aria-label={`actions-${(row as Soht2User).username}`}
            aria-controls={menuRowAnchor ? 'user-row-menu' : undefined}
            aria-haspopup="true"
            onClick={e => handleMenuRowOpen(e, row as Soht2User)}>
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
            getRowId={row => (row as Soht2User).username}
            sortingMode="client"
            loading={loading}
            sortModel={sortModel}
            columnVisibilityModel={visibility}
            onSortModelChange={handleSortModelChange}
            onColumnVisibilityModelChange={handleColumnVisibilityModelChange}
            onFilterModelChange={handleFilterModelChange}
            disableRowSelectionOnClick
            hideFooter={true}
            paginationModel={{ page: 0, pageSize: rows.length }}
            slots={{ noRowsOverlay: NoRowsOverlay, loadingOverlay: LoadingOverlay }}
            slotProps={{ noRowsOverlay: { message: 'No user records available to display.' } }}
            sx={sx}
          />
        </Box>
      </Paper>

      <Menu
        id="user-header-menu"
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
        <MenuItem onClick={handleNewUserOpen}>
          <PersonAddAlt1Icon fontSize="small" style={{ marginRight: 12 }} />
          New User
        </MenuItem>
      </Menu>

      <Menu
        id="user-row-menu"
        anchorEl={menuRowAnchor}
        open={!!menuRowAnchor}
        onClose={handleMenuRowClose}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        keepMounted>
        <MenuItem onClick={handleEditUserOpen} disabled={!selectedUser}>
          <ListItemIcon>
            <ManageAccountsIcon fontSize="small" />
          </ListItemIcon>
          Edit User
        </MenuItem>
        <MenuItem onClick={handleDeleteUserOpen} disabled={!selectedUser}>
          <ListItemIcon>
            <PersonRemoveAlt1Icon fontSize="small" />
          </ListItemIcon>
          Delete User
        </MenuItem>
      </Menu>

      <NewUserDialog open={newUserOpen} onClose={handleNewUserClose} />
      <EditUserDialog open={editUserOpen} user={selectedUser} onClose={handleEditUserClose} />
      <DeleteUserDialog open={deleteUserOpen} user={selectedUser} onClose={handleDeleteUserClose} />
    </>
  );
}

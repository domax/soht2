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
import Chip from '@mui/material/Chip';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import ListItemIcon from '@mui/material/ListItemIcon';
import PersonAddAlt1Icon from '@mui/icons-material/PersonAddAlt1';
import ManageAccountsIcon from '@mui/icons-material/ManageAccounts';
import PersonRemoveAlt1Icon from '@mui/icons-material/PersonRemoveAlt1';
import { type ApiError, type ISODateTime, type Soht2User, UserApi } from '../api/soht2Api';
import HeaderMenuButton from '../controls/HeaderMenuButton';
import NewUserDialog from './NewUserDialog';
import EditUserDialog from './EditUserDialog';
import DeleteUserDialog from './DeleteUserDialog';

type SortColumn = 'username' | 'role' | 'createdAt' | null;
export type UsersSorting = { column: SortColumn; direction: 'asc' | 'desc' | null };

export default function UsersTable({
  initSorting,
  onSortingChange,
}: Readonly<{ initSorting?: UsersSorting; onSortingChange?: (s: UsersSorting) => void }>) {
  const [users, setUsers] = useState<Soht2User[] | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const [menuHeaderAnchor, setMenuHeaderAnchor] = useState<HTMLElement | null>(null);
  const [menuRowAnchor, setMenuRowAnchor] = useState<HTMLElement | null>(null);
  const [selectedUser, setSelectedUser] = useState<Soht2User | null>(null);
  const [newUserOpen, setNewUserOpen] = useState(false);
  const [editUserOpen, setEditUserOpen] = useState(false);
  const [deleteUserOpen, setDeleteUserOpen] = useState(false);

  const load = useCallback(async () => {
    try {
      setLoading(true);
      setError(null);
      const data = await UserApi.listUsers();
      setUsers(data ?? []);
    } catch (e) {
      const apiError = e as ApiError;
      setError(apiError.message);
    } finally {
      setLoading(false);
    }
  }, []);

  const handleMenuHeaderOpen = useCallback((e: MouseEvent<HTMLElement>) => {
    setMenuHeaderAnchor(e.currentTarget);
  }, []);
  const handleMenuHeaderClose = useCallback(() => setMenuHeaderAnchor(null), []);
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
    void load();
  }, [load]);

  // Listen for external notifications about users' changes (e.g., from NewUserDialog)
  useEffect(() => {
    const handler = () => {
      void load();
    };
    window.addEventListener('users:changed', handler as EventListener);
    return () => window.removeEventListener('users:changed', handler as EventListener);
  }, [load]);

  const [sorting, setSorting] = useState(initSorting ?? { column: null, direction: null });

  const toggleSort = useCallback(
    (column: Exclude<SortColumn, null>) => {
      const nextSorting: UsersSorting = { column, direction: 'asc' };
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

  const sortedUsers = useMemo(() => {
    const list = users ?? [];
    if (!sorting.column || !sorting.direction) return list;
    const arr = [...list];
    type Col = Exclude<SortColumn, null>;
    const cmp = (a: Soht2User, b: Soht2User): number => {
      const asTime = (t?: ISODateTime | null) => (t ? new Date(t).getTime() : 0);
      const asString = (u: Soht2User) => (u[sorting.column as Col] || '').toString().toLowerCase();
      if (sorting.column === 'createdAt') return asTime(a.createdAt) - asTime(b.createdAt);
      return asString(a).localeCompare(asString(b));
    };
    arr.sort((a, b) => (sorting.direction === 'asc' ? cmp(a, b) : -cmp(a, b)));
    return arr;
  }, [users, sorting.column, sorting.direction]);

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

  const sortDir = sorting.direction ?? false;
  const dir = sorting.direction ?? 'asc';
  return (
    <>
      <TableContainer component={Paper} sx={{ height: '100%', width: '100%', overflow: 'auto' }}>
        <Table stickyHeader size="small" aria-label="users table">
          <TableHead>
            <TableRow>
              <TableCell sortDirection={sorting.column === 'username' ? sortDir : false}>
                <TableSortLabel
                  active={sorting.column === 'username'}
                  direction={dir}
                  onClick={() => toggleSort('username')}>
                  <b>Name</b>
                </TableSortLabel>
              </TableCell>
              <TableCell sortDirection={sorting.column === 'role' ? sortDir : false}>
                <TableSortLabel
                  active={sorting.column === 'role'}
                  direction={dir}
                  onClick={() => toggleSort('role')}>
                  <b>Role</b>
                </TableSortLabel>
              </TableCell>
              <TableCell sortDirection={sorting.column === 'createdAt' ? sortDir : false}>
                <TableSortLabel
                  active={sorting.column === 'createdAt'}
                  direction={dir}
                  onClick={() => toggleSort('createdAt')}>
                  <b>Created</b>
                </TableSortLabel>
              </TableCell>
              <TableCell>
                <b>Allowed Targets</b>
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
            {sortedUsers.map(u => {
              const targets = u.allowedTargets ?? [];
              return (
                <TableRow key={u.username} hover>
                  <TableCell>{u.username}</TableCell>
                  <TableCell>{(u.role || '').toString()}</TableCell>
                  <TableCell>{u.createdAt ? new Date(u.createdAt).toLocaleString() : ''}</TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                      {targets.length > 0 ? (
                        targets.map(t => <Chip key={t} label={t} size="small" variant="outlined" />)
                      ) : (
                        <span>—</span>
                      )}
                    </Box>
                  </TableCell>
                  <TableCell align="right">
                    <IconButton
                      size="small"
                      aria-label={`actions-${u.username}`}
                      aria-controls={menuRowAnchor ? 'user-row-menu' : undefined}
                      aria-haspopup="true"
                      onClick={e => handleMenuRowOpen(e, u)}>
                      <MoreVertIcon />
                    </IconButton>
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </TableContainer>

      <Menu
        id="user-header-menu"
        anchorEl={menuHeaderAnchor}
        open={!!menuHeaderAnchor}
        onClose={handleMenuHeaderClose}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        keepMounted>
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

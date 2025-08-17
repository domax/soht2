/* SOHT2 © Licensed under MIT 2025. */
import { type MouseEvent, useCallback, useEffect, useState } from 'react';
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
import Chip from '@mui/material/Chip';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import ListItemIcon from '@mui/material/ListItemIcon';
import PersonAddAlt1Icon from '@mui/icons-material/PersonAddAlt1';
import ManageAccountsIcon from '@mui/icons-material/ManageAccounts';
import PersonRemoveAlt1Icon from '@mui/icons-material/PersonRemoveAlt1';
import { useTheme } from '@mui/material/styles';
import NewUserDialog from './NewUserDialog';
import EditUserDialog from './EditUserDialog';
import DeleteUserDialog from './DeleteUserDialog';
import { type ApiError, type Soht2User, UserApi } from '../api/soht2Api';

export default function UsersTable() {
  const theme = useTheme();

  const [users, setUsers] = useState<Soht2User[] | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const [menuHeaderAnchor, setMenuHeaderAnchor] = useState<HTMLElement | null>(null);
  const [menuRowAnchor, setMenuRowAnchor] = useState<HTMLElement | null>(null);
  const [selectedUser, setSelectedUser] = useState<Soht2User | null>(null);
  const [editUserOpen, setEditUserOpen] = useState(false);
  const [deleteUserOpen, setDeleteUserOpen] = useState(false);
  const [newUserOpen, setNewUserOpen] = useState(false);

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

  const handleNewUserOpen = () => {
    setNewUserOpen(true);
    handleMenuHeaderClose();
  };

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
      <TableContainer component={Paper} sx={{ height: '100%', width: '100%', overflow: 'auto' }}>
        <Table stickyHeader size="small" aria-label="users table">
          <TableHead>
            <TableRow>
              <TableCell>
                <b>Name</b>
              </TableCell>
              <TableCell>
                <b>Role</b>
              </TableCell>
              <TableCell>
                <b>Created</b>
              </TableCell>
              <TableCell>
                <b>Allowed Targets</b>
              </TableCell>
              <TableCell align="right">
                <IconButton
                  size="small"
                  aria-label="actions-users"
                  aria-controls={menuHeaderAnchor ? 'user-header-menu' : undefined}
                  aria-haspopup="true"
                  onClick={handleMenuHeaderOpen}
                  sx={{
                    backgroundColor: theme.palette.primary.main,
                    color: theme.palette.primary.contrastText,
                    '&:hover': { backgroundColor: theme.palette.primary.light },
                  }}>
                  <MoreVertIcon />
                </IconButton>
              </TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {(users ?? []).map(u => {
              const created = u.createdAt ? new Date(u.createdAt).toLocaleString() : '';
              const targets = u.allowedTargets ?? [];
              return (
                <TableRow key={u.username} hover>
                  <TableCell>{u.username}</TableCell>
                  <TableCell>{(u.role || '').toString()}</TableCell>
                  <TableCell>{created}</TableCell>
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

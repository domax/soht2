/* SOHT2 © Licensed under MIT 2025. */
import React from 'react';
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

  const [users, setUsers] = React.useState<Soht2User[] | null>(null);
  const [loading, setLoading] = React.useState<boolean>(false);
  const [error, setError] = React.useState<string | null>(null);

  const [menuHeaderAnchor, setMenuHeaderAnchor] = React.useState<null | HTMLElement>(null);
  const [menuRowAnchor, setMenuRowAnchor] = React.useState<null | HTMLElement>(null);
  const [selectedUser, setSelectedUser] = React.useState<Soht2User | null>(null);
  const [editOpen, setEditOpen] = React.useState(false);
  const [deleteOpen, setDeleteOpen] = React.useState(false);
  const [newUserOpen, setNewUserOpen] = React.useState(false);

  const load = React.useCallback(async () => {
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

  const openMenuHeader = (event: React.MouseEvent<HTMLElement>) => {
    setMenuHeaderAnchor(event.currentTarget);
  };
  const closeMenuHeader = () => setMenuHeaderAnchor(null);

  const openNewUser = () => {
    setNewUserOpen(true);
    closeMenuHeader();
  };

  const openMenuRow = (event: React.MouseEvent<HTMLElement>, user: Soht2User) => {
    setSelectedUser(user);
    setMenuRowAnchor(event.currentTarget);
  };
  const closeMenuRow = () => setMenuRowAnchor(null);

  const onEdit = () => {
    closeMenuRow();
    setEditOpen(true);
  };
  const onDelete = () => {
    closeMenuRow();
    setDeleteOpen(true);
  };

  React.useEffect(() => {
    void load();
  }, [load]);

  // Listen for external notifications about users' changes (e.g., from NewUserDialog)
  React.useEffect(() => {
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
                  onClick={openMenuHeader}
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
                      onClick={e => openMenuRow(e, u)}>
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
        onClose={closeMenuHeader}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        keepMounted>
        <MenuItem onClick={openNewUser}>
          <PersonAddAlt1Icon fontSize="small" style={{ marginRight: 12 }} />
          New User
        </MenuItem>
      </Menu>

      <Menu
        id="user-row-menu"
        anchorEl={menuRowAnchor}
        open={!!menuRowAnchor}
        onClose={closeMenuRow}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
        keepMounted>
        <MenuItem onClick={onEdit} disabled={!selectedUser}>
          <ListItemIcon>
            <ManageAccountsIcon fontSize="small" />
          </ListItemIcon>
          Edit User
        </MenuItem>
        <MenuItem onClick={onDelete} disabled={!selectedUser}>
          <ListItemIcon>
            <PersonRemoveAlt1Icon fontSize="small" />
          </ListItemIcon>
          Delete User
        </MenuItem>
      </Menu>

      <NewUserDialog open={newUserOpen} onClose={() => setNewUserOpen(false)} />
      <EditUserDialog open={editOpen} user={selectedUser} onClose={() => setEditOpen(false)} />
      <DeleteUserDialog
        open={deleteOpen}
        user={selectedUser}
        onClose={() => setDeleteOpen(false)}
      />
    </>
  );
}

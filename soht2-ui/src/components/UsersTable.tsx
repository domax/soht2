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
import { type ApiError, type Soht2User, UserApi } from '../api/soht2Api';

export default function UsersTable() {
  const [users, setUsers] = React.useState<Soht2User[] | null>(null);
  const [loading, setLoading] = React.useState<boolean>(false);
  const [error, setError] = React.useState<string | null>(null);

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
            <TableCell align="right" />
          </TableRow>
        </TableHead>
        <TableBody>
          {(users ?? []).map(u => {
            const created = u.createdAt ? new Date(u.createdAt).toLocaleString() : '';
            const targets = (u.allowedTargets ?? []).join(', ');
            return (
              <TableRow key={u.username} hover>
                <TableCell>{u.username}</TableCell>
                <TableCell>{(u.role || '').toString()}</TableCell>
                <TableCell>{created}</TableCell>
                <TableCell>{targets}</TableCell>
                <TableCell align="right">
                  <IconButton size="small" aria-label={`actions-${u.username}`}>
                    <MoreVertIcon />
                  </IconButton>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

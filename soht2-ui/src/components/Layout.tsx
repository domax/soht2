import React from 'react';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import Container from '@mui/material/Container';
import IconButton from '@mui/material/IconButton';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Divider from '@mui/material/Divider';
import Tooltip from '@mui/material/Tooltip';
import MenuIcon from '@mui/icons-material/Menu';
import DarkModeIcon from '@mui/icons-material/DarkMode';
import LightModeIcon from '@mui/icons-material/LightMode';
import LogoutIcon from '@mui/icons-material/Logout';
import LockResetIcon from '@mui/icons-material/LockReset';
import { useNavigate, useLocation } from 'react-router-dom';
import { useThemeMode } from '../theme';
import { httpClient } from '../api/soht2Api';
import soht2Logo from '/soht2_logo.png'; // NOSONAR typescript:S6859
import ChangePasswordDialog from './ChangePasswordDialog';

export function Layout({ children }: Readonly<{ children: React.ReactNode }>) {
  const { mode, toggle } = useThemeMode();
  const [anchorEl, setAnchorEl] = React.useState<null | HTMLElement>(null);
  const menuOpen = Boolean(anchorEl);
  const navigate = useNavigate();
  const location = useLocation();
  const isLogin = location.pathname === '/login';

  // Change password dialog state (moved to component)
  const [pwDialogOpen, setPwDialogOpen] = React.useState(false);

  const handleMenu = (event: React.MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  };
  const handleClose = () => setAnchorEl(null);

  const handleToggleTheme = () => {
    toggle();
    handleClose();
  };

  const handleLogout = () => {
    httpClient.clearAuth();
    handleClose();
    navigate('/login', { replace: true });
  };

  const openChangePassword = () => {
    setPwDialogOpen(true);
    handleClose();
  };

  const closeChangePassword = () => {
    setPwDialogOpen(false);
  };

  const ThemeIcon = mode === 'dark' ? LightModeIcon : DarkModeIcon;
  const themeText = mode === 'dark' ? 'Light theme' : 'Dark theme';

  return (
    <>
      <AppBar position="fixed">
        <Toolbar>
          <img src={soht2Logo} alt="Logo" style={{ height: 40, marginRight: 16 }} />
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            SOHT2 Server
          </Typography>
          <Tooltip title="Menu">
            <IconButton color="inherit" onClick={handleMenu} size="large" aria-label="menu">
              <MenuIcon />
            </IconButton>
          </Tooltip>
          <Menu
            anchorEl={anchorEl}
            open={menuOpen}
            onClose={handleClose}
            anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
            transformOrigin={{ vertical: 'top', horizontal: 'right' }}
            keepMounted>
            <MenuItem onClick={handleToggleTheme}>
              <ThemeIcon fontSize="small" style={{ marginRight: 12 }} />
              {themeText}
            </MenuItem>
            {!isLogin && (
              <MenuItem onClick={openChangePassword}>
                <LockResetIcon fontSize="small" style={{ marginRight: 12 }} />
                Change Password
              </MenuItem>
            )}
            {!isLogin && <Divider />}
            {!isLogin && (
              <MenuItem onClick={handleLogout}>
                <LogoutIcon fontSize="small" style={{ marginRight: 12 }} />
                Log out
              </MenuItem>
            )}
          </Menu>
        </Toolbar>
      </AppBar>
      {/* Spacer to offset fixed AppBar height */}
      <Toolbar />
      <Container maxWidth={false} disableGutters sx={{ p: 2, position: 'fixed', top: 64, left: 0 }}>
        {children}
      </Container>

      <ChangePasswordDialog open={pwDialogOpen} onClose={closeChangePassword} />
    </>
  );
}

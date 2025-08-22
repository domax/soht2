/* SOHT2 © Licensed under MIT 2025. */
import { type MouseEvent, type ReactNode, useCallback, useState } from 'react';
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
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import { useLocation, useNavigate } from 'react-router-dom';
import soht2Logo40 from '../assets/soht2_logo_40.png';
import { useThemeMode } from '../theme';
import { httpClient } from '../api/soht2Api';
import ChangePasswordDialog from './ChangePasswordDialog';
import ErrorAlert from './ErrorAlert';
import type { WindowProps } from '../App';

const swaggerUrl = (window as WindowProps).__SWAGGER_URL__ ?? null;

export default function Layout({ children }: Readonly<{ children: ReactNode }>) {
  const { mode, toggle } = useThemeMode();
  const [anchorEl, setAnchorEl] = useState<null | HTMLElement>(null);
  const navigate = useNavigate();
  const location = useLocation();
  const isLogin = location.pathname === '/login';

  const [pwDialogOpen, setPwDialogOpen] = useState(false);

  const handleMenu = useCallback((event: MouseEvent<HTMLElement>) => {
    setAnchorEl(event.currentTarget);
  }, []);
  const handleClose = useCallback(() => setAnchorEl(null), []);

  const handleToggleTheme = useCallback(() => {
    toggle();
    handleClose();
  }, [toggle, handleClose]);

  const handleLogout = useCallback(() => {
    httpClient.clearAuth();
    handleClose();
    navigate('/login', { replace: true });
  }, [handleClose, navigate]);

  const openChangePassword = useCallback(() => {
    setPwDialogOpen(true);
    handleClose();
  }, [handleClose]);

  const closeChangePassword = useCallback(() => {
    setPwDialogOpen(false);
  }, []);

  const ThemeIcon = mode === 'dark' ? LightModeIcon : DarkModeIcon;
  const themeText = mode === 'dark' ? 'Light Theme' : 'Dark Theme';

  return (
    <>
      <AppBar position="fixed">
        <Toolbar>
          <img src={soht2Logo40} alt="Logo" style={{ height: 40, marginRight: 16 }} />
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
            open={!!anchorEl}
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
            {swaggerUrl && (
              <MenuItem component="a" href={swaggerUrl} target="_blank" rel="noopener noreferrer">
                <OpenInNewIcon fontSize="small" style={{ marginRight: 12 }} />
                API Playground
              </MenuItem>
            )}
            {!isLogin && <Divider />}
            {!isLogin && (
              <MenuItem onClick={handleLogout}>
                <LogoutIcon fontSize="small" style={{ marginRight: 12 }} />
                Log Out
              </MenuItem>
            )}
          </Menu>
        </Toolbar>
      </AppBar>
      <Container maxWidth={false} disableGutters sx={{ p: 2, position: 'fixed', top: 64, left: 0 }}>
        {children}
      </Container>

      <ChangePasswordDialog open={pwDialogOpen} onClose={closeChangePassword} />
      <ErrorAlert />
    </>
  );
}

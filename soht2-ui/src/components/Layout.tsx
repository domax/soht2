import React from 'react';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import Container from '@mui/material/Container';
import soht2Logo from '/soht2_logo.png'; // NOSONAR typescript:S6859

export function Layout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <>
      <AppBar position="fixed">
        <Toolbar>
          <img src={soht2Logo} alt="Logo" style={{ height: 40, marginRight: 16 }} />
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            SOHT2 Server
          </Typography>
        </Toolbar>
      </AppBar>
      {/* Spacer to offset fixed AppBar height */}
      <Toolbar />
      <Container maxWidth={false} disableGutters sx={{ p: 2 }}>
        {children}
      </Container>
    </>
  );
}

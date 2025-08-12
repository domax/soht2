import React from 'react';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import Container from '@mui/material/Container';
import soht2Logo from '/soht2_logo.png'; // NOSONAR typescript:S6859

export function Layout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <>
      <AppBar position="static">
        <Toolbar>
          <img src={soht2Logo} alt="Logo" style={{ height: 40, marginRight: 16 }} />
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            SOHT2 Server
          </Typography>
        </Toolbar>
      </AppBar>
      <Container sx={{ marginTop: 4 }}>{children}</Container>
    </>
  );
}

import soht2Logo from '/soht2_logo.png'; // NOSONAR typescript:S6859
import './App.css';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import Container from '@mui/material/Container';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';

function Layout({ children }: Readonly<{ children: React.ReactNode }>) {
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

function LoginPage() {
  return (
    <Layout>
      <TextField label="Username" variant="outlined" fullWidth margin="normal" />
      <TextField label="Password" type="password" variant="outlined" fullWidth margin="normal" />
      <Button variant="contained" color="primary">
        Login
      </Button>
    </Layout>
  );
}

function AdminPage() {
  return <Layout>{/* Admin page content */}</Layout>;
}

function UserPage() {
  return <Layout>{/* User page content */}</Layout>;
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/admin" element={<AdminPage />} />
        <Route path="/user" element={<UserPage />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;

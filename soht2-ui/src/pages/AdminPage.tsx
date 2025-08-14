/* SOHT2 © Licensed under MIT 2025. */
import React from 'react';
import { Layout } from '../components/Layout';
import { Navigate } from 'react-router-dom';
import { httpClient, type Soht2User } from '../api/soht2Api.ts';
import Box from '@mui/material/Box';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Typography from '@mui/material/Typography';
import UsersTable from '../components/UsersTable';

function TabPanel(props: Readonly<{ children?: React.ReactNode; index: number; value: number }>) {
  const { children, value, index, ...other } = props;
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`admin-tabpanel-${index}`}
      aria-labelledby={`admin-tab-${index}`}
      {...other}
      style={{ height: '100%' }}>
      {value === index && <Box sx={{ p: 0, height: '100%' }}>{children}</Box>}
    </div>
  );
}

export default function AdminPage({ user }: Readonly<{ user?: Soht2User | null }>) {
  const [tab, setTab] = React.useState(0);

  if ((user?.role || '').toUpperCase() !== 'ADMIN') {
    httpClient.clearAuth();
    return <Navigate to="/login" replace />;
  }

  const handleChange = (_: React.SyntheticEvent, newValue: number) => setTab(newValue);

  return (
    <Layout>
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          height: 'calc(100vh - 64px - 16px)',
          width: '100%',
        }}>
        <Tabs value={tab} onChange={handleChange} aria-label="admin tabs">
          <Tab label="Users" id="admin-tab-0" aria-controls="admin-tabpanel-0" />
          <Tab label="Connections" id="admin-tab-1" aria-controls="admin-tabpanel-1" />
          <Tab label="History" id="admin-tab-2" aria-controls="admin-tabpanel-2" />
        </Tabs>
        <Box sx={{ flex: 1, minHeight: 0 }}>
          <TabPanel value={tab} index={0}>
            <Box sx={{ height: '100%' }}>
              <UsersTable />
            </Box>
          </TabPanel>
          <TabPanel value={tab} index={1}>
            <Box sx={{ p: 2 }}>
              <Typography>Under construction</Typography>
            </Box>
          </TabPanel>
          <TabPanel value={tab} index={2}>
            <Box sx={{ p: 2 }}>
              <Typography>Under construction</Typography>
            </Box>
          </TabPanel>
        </Box>
      </Box>
    </Layout>
  );
}

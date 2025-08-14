/* SOHT2 © Licensed under MIT 2025. */
import React from 'react';
import { Navigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Typography from '@mui/material/Typography';
import { httpClient, type Soht2User } from '../api/soht2Api.ts';
import Layout from '../components/Layout';
import TabPanel from '../components/TabPanel';
import UsersTable from '../components/UsersTable';

export default function AdminPage({ user }: Readonly<{ user?: Soht2User | null }>) {
  const prefix = 'admin-';

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
          height: 'calc(100vh - 80px)',
          width: '100%',
        }}>
        <Tabs value={tab} onChange={handleChange} aria-label="admin tabs">
          <Tab label="Users" id={`${prefix}tab-0`} aria-controls={`${prefix}tabpanel-0`} />
          <Tab label="Connections" id={`${prefix}tab-1`} aria-controls={`${prefix}tabpanel-1`} />
          <Tab label="History" id={`${prefix}tab-2`} aria-controls={`${prefix}tabpanel-2`} />
        </Tabs>
        <Box sx={{ flex: 1, minHeight: 0 }}>
          <TabPanel prefix={prefix} value={tab} index={0}>
            <UsersTable />
          </TabPanel>
          <TabPanel prefix={prefix} value={tab} index={1}>
            <Box sx={{ p: 2 }}>
              <Typography>Under construction</Typography>
            </Box>
          </TabPanel>
          <TabPanel prefix={prefix} value={tab} index={2}>
            <Box sx={{ p: 2 }}>
              <Typography>Under construction</Typography>
            </Box>
          </TabPanel>
        </Box>
      </Box>
    </Layout>
  );
}

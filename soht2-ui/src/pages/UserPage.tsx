/* SOHT2 © Licensed under MIT 2025. */
import React from 'react';
import { Layout } from '../components/Layout';
import { Navigate } from 'react-router-dom';
import { httpClient, type Soht2User } from '../api/soht2Api.ts';
import Box from '@mui/material/Box';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Typography from '@mui/material/Typography';

function TabPanel(props: Readonly<{ children?: React.ReactNode; index: number; value: number }>) {
  const { children, value, index, ...other } = props;
  return (
    <div
      role="tabpanel"
      hidden={value !== index}
      id={`user-tabpanel-${index}`}
      aria-labelledby={`user-tab-${index}`}
      {...other}
      style={{ height: '100%' }}>
      {value === index && <Box sx={{ p: 0, height: '100%' }}>{children}</Box>}
    </div>
  );
}

export default function UserPage({ user }: Readonly<{ user?: Soht2User | null }>) {
  const [tab, setTab] = React.useState(0);

  if ((user?.role || '') === '') {
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
        <Tabs value={tab} onChange={handleChange} aria-label="user tabs">
          <Tab label="Connections" id="user-tab-0" aria-controls="user-tabpanel-0" />
          <Tab label="History" id="user-tab-1" aria-controls="user-tabpanel-1" />
        </Tabs>
        <Box sx={{ flex: 1, minHeight: 0 /* allow children to use 100% height with overflow */ }}>
          <TabPanel value={tab} index={0}>
            <Box sx={{ p: 2 }}>
              <Typography>Under construction</Typography>
            </Box>
          </TabPanel>
          <TabPanel value={tab} index={1}>
            <Box sx={{ p: 2 }}>
              <Typography>Under construction</Typography>
            </Box>
          </TabPanel>
        </Box>
      </Box>
    </Layout>
  );
}

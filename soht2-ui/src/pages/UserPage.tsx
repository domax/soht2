/* SOHT2 © Licensed under MIT 2025. */
import { type SyntheticEvent, useCallback, useState } from 'react';
import { Navigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Typography from '@mui/material/Typography';
import { httpClient, type Soht2User } from '../api/soht2Api.ts';
import Layout from '../components/Layout';
import TabPanel from '../components/TabPanel';

export default function UserPage({ user }: Readonly<{ user?: Soht2User | null }>) {
  const prefix = 'user-';

  const [tab, setTab] = useState(0);

  const handleChange = useCallback((_: SyntheticEvent, newTab: number) => setTab(newTab), []);

  if ((user?.role || '') === '') {
    httpClient.clearAuth();
    return <Navigate to="/login" replace />;
  }

  return (
    <Layout>
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          height: 'calc(100vh - 80px)',
          width: '100%',
        }}>
        <Tabs value={tab} onChange={handleChange} aria-label="user tabs">
          <Tab label="Connections" id={`${prefix}tab-0`} aria-controls={`${prefix}tabpanel-0`} />
          <Tab label="History" id={`${prefix}tab-1`} aria-controls={`${prefix}tabpanel-1`} />
        </Tabs>
        <Box sx={{ flex: 1, minHeight: 0 }}>
          <TabPanel prefix={prefix} value={tab} index={0}>
            <Box sx={{ p: 2 }}>
              <Typography>Under construction</Typography>
            </Box>
          </TabPanel>
          <TabPanel prefix={prefix} value={tab} index={1}>
            <Box sx={{ p: 2 }}>
              <Typography>Under construction</Typography>
            </Box>
          </TabPanel>
        </Box>
      </Box>
    </Layout>
  );
}

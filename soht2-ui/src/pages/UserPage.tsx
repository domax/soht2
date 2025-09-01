/* SOHT2 © Licensed under MIT 2025. */
import { type SyntheticEvent, useCallback, useState } from 'react';
import { Navigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import { httpClient, type Soht2User } from '../api/soht2Api';
import Layout from '../components/Layout';
import TabPanel from '../components/TabPanel';
import ConnectionsTable, { type ConnectionSettings } from '../components/ConnectionsTable';
import HistoryTable, { type HistorySettings } from '../components/HistoryTable';

export default function UserPage({ user }: Readonly<{ user?: Soht2User | null }>) {
  const prefix = 'user-';

  const [tab, setTab] = useState(0);
  const handleChange = useCallback((_: SyntheticEvent, newTab: number) => setTab(newTab), []);

  const [connectionsSettings, setConnectionsSettings] = useState<ConnectionSettings>({
    sorting: { column: 'openedAt', direction: 'desc' },
    visibility: {},
    filters: [],
    autoRefresh: false,
  });

  const [historySettings, setHistorySettings] = useState<HistorySettings>({
    requestParams: { sort: ['openedAt:desc'] },
    visibility: {},
  });

  if ((user?.role ?? '') === '') {
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
            <ConnectionsTable
              initSettings={connectionsSettings}
              onSettingsChange={setConnectionsSettings}
            />
          </TabPanel>
          <TabPanel prefix={prefix} value={tab} index={1}>
            <HistoryTable
              regularUser={user?.username ?? ''}
              initSettings={historySettings}
              onSettingsChange={setHistorySettings}
            />
          </TabPanel>
        </Box>
      </Box>
    </Layout>
  );
}

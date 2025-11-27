/* SOHT2 © Licensed under MIT 2025. */
import { type SyntheticEvent, useCallback, useState } from 'react';
import { Navigate } from 'react-router-dom';
import Box from '@mui/material/Box';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import { httpClient, type Soht2User } from '../api/soht2Api';
import Layout from '../components/Layout';
import TabPanel from '../components/TabPanel';
import UsersTable, { type UserSettings } from '../components/UsersTable';
import ConnectionsTable, { type ConnectionSettings } from '../components/ConnectionsTable';
import HistoryTable, { DEF_PG_SZ, type HistorySettings } from '../components/HistoryTable';

export default function AdminPage({ user }: Readonly<{ user?: Soht2User | null }>) {
  const prefix = 'admin-';

  const [tab, setTab] = useState(0);
  const handleTabChange = useCallback((_: SyntheticEvent, newTab: number) => setTab(newTab), []);

  const [usersSettings, setUsersSettings] = useState<UserSettings>({
    sorting: { column: 'username', direction: 'asc' },
    filters: [],
    visibility: {},
  });

  const [connectionsSettings, setConnectionsSettings] = useState<ConnectionSettings>({
    sorting: { column: 'openedAt', direction: 'desc' },
    visibility: {},
    filters: [],
    autoRefresh: false,
  });

  const [historySettings, setHistorySettings] = useState<HistorySettings>({
    requestParams: { sort: ['openedAt:desc'], sz: DEF_PG_SZ, pg: 0 },
    visibility: {},
  });

  if ((user?.role ?? '').toUpperCase() !== 'ADMIN') {
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
        <Tabs value={tab} onChange={handleTabChange} aria-label="admin tabs">
          <Tab label="Users" id={`${prefix}tab-0`} aria-controls={`${prefix}tabpanel-0`} />
          <Tab label="Connections" id={`${prefix}tab-1`} aria-controls={`${prefix}tabpanel-1`} />
          <Tab label="History" id={`${prefix}tab-2`} aria-controls={`${prefix}tabpanel-2`} />
        </Tabs>
        <Box sx={{ flex: 1, minHeight: 0 }}>
          <TabPanel prefix={prefix} value={tab} index={0}>
            <UsersTable initSettings={usersSettings} onSettingsChange={setUsersSettings} />
          </TabPanel>
          <TabPanel prefix={prefix} value={tab} index={1}>
            <ConnectionsTable
              initSettings={connectionsSettings}
              onSettingsChange={setConnectionsSettings}
            />
          </TabPanel>
          <TabPanel prefix={prefix} value={tab} index={2}>
            <HistoryTable initSettings={historySettings} onSettingsChange={setHistorySettings} />
          </TabPanel>
        </Box>
      </Box>
    </Layout>
  );
}

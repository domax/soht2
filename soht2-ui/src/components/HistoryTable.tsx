/* SOHT2 © Licensed under MIT 2025. */
import { useCallback, useEffect, useMemo, useState } from 'react';
import Paper from '@mui/material/Paper';
import Box from '@mui/material/Box';
import { useTheme } from '@mui/material/styles';
import {
  DataGrid,
  getGridNumericOperators,
  getGridStringOperators,
  type GridColDef,
  type GridFilterModel,
  type GridPaginationModel,
  type GridSortModel,
} from '@mui/x-data-grid';
import {
  type ApiError,
  ConnectionApi,
  type HistoryFilters,
  type HistoryRequestParams,
  type HistorySortColumn,
  type Soht2Connection,
  type SortingDirLower,
} from '../api/soht2Api';
import {
  formatBytes,
  formatDateTime,
  getDataGridStyle,
  getDateTimeFilter,
  getDateTimeFilterItem,
  getDateTimeOperators,
  getNumberArrayFilter,
  getPortFilterItem,
  getStringFilter,
  getStringFilterItem,
} from '../api/functions';
import { dispatchAppErrorEvent } from '../api/appEvents';
import { useDebounce } from '../hooks';
import { LoadingOverlay, NoRowsOverlay } from '../controls/dataGridOverlays';

type ConnectionVisibilityColumn = Exclude<HistorySortColumn, 'connectionId'>;
export type HistoryVisibility = { [K in ConnectionVisibilityColumn]?: boolean };

export type HistorySettings = {
  visibility: HistoryVisibility;
  requestParams: HistoryRequestParams;
};

function getGridFilterModel(historyFilters?: HistoryFilters): GridFilterModel {
  const hf = historyFilters ?? {};
  const items = [
    getStringFilterItem('id', hf.id) ?? null,
    getStringFilterItem('username', hf.un) ?? null,
    getStringFilterItem('clientHost', hf.ch) ?? null,
    getStringFilterItem('targetHost', hf.th) ?? null,
    getPortFilterItem('targetPort', hf.tp) ?? null,
    getDateTimeFilterItem('openedAt', hf.oa, hf.ob) ?? null,
    getDateTimeFilterItem('closedAt', hf.ca, hf.cb) ?? null,
  ].filter(v => v !== null);
  return { items };
}

function getHistoryFilters(filterModel?: GridFilterModel): HistoryFilters {
  const items = filterModel?.items ?? [];
  const opened = getDateTimeFilter(items.find(f => f.field === 'openedAt'));
  const closed = getDateTimeFilter(items.find(f => f.field === 'closedAt'));
  return {
    id: getStringFilter(items.find(f => f.field === 'id')),
    un: getStringFilter(items.find(f => f.field === 'username')),
    ch: getStringFilter(items.find(f => f.field === 'clientHost')),
    th: getStringFilter(items.find(f => f.field === 'targetHost')),
    tp: getNumberArrayFilter(items.find(f => f.field === 'targetPort')),
    oa: opened ? opened.after : undefined,
    ob: opened ? opened.before : undefined,
    ca: closed ? closed.after : undefined,
    cb: closed ? closed.before : undefined,
  };
}

export default function HistoryTable({
  regularUser,
  initSettings,
  onSettingsChange,
}: Readonly<{
  regularUser?: string;
  initSettings?: HistorySettings;
  onSettingsChange?: (s: HistorySettings) => void;
}>) {
  const theme = useTheme();

  const [visibility, setVisibility] = useState(initSettings?.visibility ?? {});
  const [sortModel, setSortModel] = useState<GridSortModel>(() => {
    const m = initSettings?.requestParams?.sort ?? [];
    if (m.length === 0) return [];
    const [c, d] = m[0].split(':');
    return [{ field: c as HistorySortColumn, sort: d.toLowerCase() as SortingDirLower }];
  });
  const [paginationModel, setPaginationModel] = useState<GridPaginationModel>({
    page: initSettings?.requestParams?.pg ?? 0,
    pageSize: initSettings?.requestParams?.sz ?? 50,
  });
  const [filterModel, setFilterModel] = useState<GridFilterModel>(() =>
    getGridFilterModel(initSettings?.requestParams)
  );

  const [requestParams, setRequestParams] = useState<HistoryRequestParams>(
    initSettings?.requestParams ?? {}
  );
  const [rows, setRows] = useState<Soht2Connection[]>([]);
  const [rowCount, setRowCount] = useState(0);
  const [loading, setLoading] = useState<boolean>(false);

  const load = useCallback(async (param: HistoryRequestParams) => {
    try {
      setLoading(true);
      const res = await ConnectionApi.history(param);
      setRows((res?.data ?? []).map(c => ({ ...c, username: c.user?.username ?? '' })));
      setRowCount(res?.totalItems ?? 0);
    } catch (e) {
      dispatchAppErrorEvent(e as ApiError);
    } finally {
      setLoading(false);
    }
  }, []);
  useEffect(() => void load(requestParams), [load, requestParams]);

  useEffect(() => {
    const [si] = sortModel;
    const sort = si?.field && si?.sort ? [`${si.field}:${si.sort}`] : undefined;
    const pg = paginationModel.page;
    const sz = paginationModel.pageSize;
    setRequestParams(p => ({ ...p, sort, pg, sz }));
  }, [paginationModel.page, paginationModel.pageSize, sortModel]);

  const handleFilterModel = useCallback(() => {
    setRequestParams(op => {
      const np = { ...op, ...getHistoryFilters(filterModel) };
      return JSON.stringify(op) === JSON.stringify(np) ? op : np;
    });
  }, [filterModel]);
  useDebounce(700, handleFilterModel, [handleFilterModel]);

  useEffect(() => {
    if (onSettingsChange) onSettingsChange({ visibility, requestParams });
  }, [onSettingsChange, requestParams, visibility]);

  const columns = useMemo<GridColDef[]>(() => {
    const stringOperators = getGridStringOperators().filter(
      op => !op.value.startsWith('doesNot') && !op.value.startsWith('is')
    );
    const portOperators = getGridNumericOperators().filter(
      op => op.value === '=' || op.value === 'isAnyOf'
    );
    const dateTimeOperators = getDateTimeOperators();
    const cols: GridColDef[] = [
      {
        field: 'id',
        type: 'string',
        headerName: 'Connection ID',
        flex: 1.5,
        minWidth: 330,
        hideable: false,
        renderCell: params => <pre style={{ margin: 0 }}>{params.value ?? ''}</pre>,
        filterOperators: stringOperators,
      },
      {
        field: 'username',
        type: 'string',
        headerName: 'User',
        flex: 0.7,
        minWidth: 120,
        filterOperators: stringOperators,
      },
      {
        field: 'clientHost',
        type: 'string',
        headerName: 'Client Host',
        flex: 0.7,
        minWidth: 120,
        filterOperators: stringOperators,
      },
      {
        field: 'targetHost',
        type: 'string',
        headerName: 'Target Host',
        flex: 0.7,
        minWidth: 120,
        filterOperators: stringOperators,
      },
      {
        field: 'targetPort',
        type: 'number',
        headerName: 'Target Port',
        flex: 0.3,
        minWidth: 100,
        align: 'right',
        valueGetter: value => Number(value),
        filterOperators: portOperators,
      },
      {
        field: 'openedAt',
        type: 'dateTime',
        headerName: 'Opened',
        flex: 0.5,
        minWidth: 150,
        valueGetter: value => new Date(value),
        renderCell: params => (params.value ? formatDateTime(params.value) : ''),
        filterOperators: dateTimeOperators,
      },
      {
        field: 'closedAt',
        type: 'dateTime',
        headerName: 'Closed',
        flex: 0.5,
        minWidth: 150,
        valueGetter: value => new Date(value),
        renderCell: params => (params.value ? formatDateTime(params.value) : ''),
        filterOperators: dateTimeOperators,
      },
      {
        field: 'bytesRead',
        type: 'number',
        headerName: 'Read',
        filterable: false,
        flex: 0.5,
        minWidth: 120,
        valueGetter: value => Number(value),
        renderCell: params => formatBytes(params.value ?? 0),
      },
      {
        field: 'bytesWritten',
        type: 'number',
        headerName: 'Written',
        filterable: false,
        flex: 0.5,
        minWidth: 120,
        valueGetter: value => Number(value),
        renderCell: params => formatBytes(params.value ?? 0),
      },
    ];
    return cols.filter(c => c.field !== 'username' || !regularUser);
  }, [regularUser]);

  return (
    <Paper sx={{ height: '100%', width: '100%', display: 'flex', flexDirection: 'column' }}>
      <Box sx={{ flex: 1 }}>
        <DataGrid
          columns={columns}
          rows={rows}
          rowCount={rowCount}
          getRowId={(row: Soht2Connection) => row.id}
          sortingMode="server"
          filterMode="server"
          paginationMode="server"
          loading={loading}
          sortModel={sortModel}
          filterModel={filterModel}
          paginationModel={paginationModel}
          columnVisibilityModel={visibility}
          pageSizeOptions={[20, 50, 100]}
          onSortModelChange={setSortModel}
          onColumnVisibilityModelChange={setVisibility}
          onFilterModelChange={setFilterModel}
          onPaginationModelChange={setPaginationModel}
          disableRowSelectionOnClick
          slots={{ noRowsOverlay: NoRowsOverlay, loadingOverlay: LoadingOverlay }}
          slotProps={{ noRowsOverlay: { message: 'No history records found.' } }}
          sx={{ ...getDataGridStyle(theme) }}
        />
      </Box>
    </Paper>
  );
}

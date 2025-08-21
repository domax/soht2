/* SOHT2 © Licensed under MIT 2025. */
import { useCallback, useEffect, useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import Stack from '@mui/material/Stack';
import Grid2 from '@mui/material/Grid2';
import Typography from '@mui/material/Typography';
import MultiInputField from '../controls/MultiInputField';
import type { UUID } from '../api/soht2Api';

export type HistoryFilters = {
  un?: string[]; // Users
  id?: string[]; // UUIDs
  ch?: string; // Client Host contains
  th?: string; // Target Host contains
  tp?: number[]; // Target Ports
  oa?: string; // Opened After (ISO)
  ob?: string; // Opened Before (ISO)
  ca?: string; // Closed After (ISO)
  cb?: string; // Closed Before (ISO)
};

const RE_USERNAME = /^\w{3,30}$/;
const RE_UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-5][0-9a-f]{3}-[089ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

export default function HistoryFiltersDialog({
  open,
  regularUser,
  value,
  onApply,
  onClose,
}: Readonly<{
  open: boolean;
  regularUser?: string;
  value?: HistoryFilters;
  onApply: (filters: HistoryFilters) => void;
  onClose: () => void;
}>) {
  const [draft, setDraft] = useState<HistoryFilters>({});

  useEffect(() => {
    setDraft(
      value
        ? {
            ...value,
            un: regularUser ? [regularUser] : value.un,
            oa: value.oa ? new Date(value.oa).toISOString().split('T')[0] : '',
            ob: value.ob ? new Date(value.ob).toISOString().split('T')[0] : '',
            ca: value.ca ? new Date(value.ca).toISOString().split('T')[0] : '',
            cb: value.cb ? new Date(value.cb).toISOString().split('T')[0] : '',
          }
        : {}
    );
  }, [value, open, regularUser]);

  const handleReset = useCallback(
    () => setDraft({ un: regularUser ? [regularUser] : undefined }),
    [regularUser]
  );

  const handleApply = useCallback(() => {
    // Sanitize and apply
    const cleaned: HistoryFilters = {};
    if (regularUser) cleaned.un = [regularUser];
    else if (draft.un?.length) cleaned.un = draft.un;
    if (draft.id?.length) cleaned.id = draft.id;
    if (draft.ch) cleaned.ch = draft.ch;
    if (draft.th) cleaned.th = draft.th;
    if (draft.tp?.length) cleaned.tp = draft.tp;
    if (draft.oa) cleaned.oa = new Date(draft.oa).toISOString().split('T')[0] + 'T00:00:00.000';
    if (draft.ob) cleaned.ob = new Date(draft.ob).toISOString().split('T')[0] + 'T23:59:59.999';
    if (draft.ca) cleaned.ca = new Date(draft.ca).toISOString().split('T')[0] + 'T00:00:00.000';
    if (draft.cb) cleaned.cb = new Date(draft.cb).toISOString().split('T')[0] + 'T23:59:59.999';
    onApply(cleaned);
  }, [
    draft.ca,
    draft.cb,
    draft.ch,
    draft.id,
    draft.oa,
    draft.ob,
    draft.th,
    draft.tp,
    draft.un,
    onApply,
    regularUser,
  ]);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>History Filters</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Grid2 container spacing={2}>
            <Grid2 size={6}>
              <MultiInputField<string>
                label="Users"
                placeholder={regularUser ? 'Read only' : 'Enter username'}
                helperText={regularUser ? '' : undefined}
                values={draft?.un ?? []}
                readonly={!!regularUser}
                valueInputPredicate={v => RE_USERNAME.test(v)}
                valueErrorHint="Username must be 3-30 alpanumeric characters."
                stringToValue={v => v.toLowerCase()}
                onChange={un => setDraft(d => ({ ...d, un }))}
              />
            </Grid2>
            <Grid2 size={6}>
              <MultiInputField<UUID>
                label="Connection IDs"
                placeholder="Enter connection ID"
                values={draft?.id ?? []}
                valueInputPredicate={v => RE_UUID.test(v)}
                valueErrorHint="ID must be a UUID v4."
                stringToValue={v => v.toLowerCase()}
                onChange={id => setDraft(d => ({ ...d, id }))}
              />
            </Grid2>
            <Grid2 size={3}>
              <TextField
                label="Client Host"
                value={draft.ch ?? ''}
                onChange={e => setDraft(d => ({ ...d, ch: e.target.value }))}
                helperText="Text occurs in the host name"
                fullWidth
              />
            </Grid2>
            <Grid2 size={3}>
              <TextField
                label="Target Host"
                value={draft.th ?? ''}
                onChange={e => setDraft(d => ({ ...d, th: e.target.value }))}
                helperText="Text occurs in the host name"
                fullWidth
              />
            </Grid2>
            <Grid2 size={6}>
              <MultiInputField<number>
                label="Target Ports"
                placeholder="Enter username"
                values={draft?.tp ?? []}
                valueInputPredicate={v => !isNaN(Number(v))}
                valueErrorHint="Target port must be a number."
                valueToString={v => String(v)}
                stringToValue={v => Number(v)}
                onChange={tp => setDraft(d => ({ ...d, tp }))}
              />
            </Grid2>
          </Grid2>

          <Grid2 container spacing={2}>
            <Grid2 size={6}>
              <Typography variant="subtitle2" sx={{ paddingBottom: 2 }}>
                Opened
              </Typography>
              <Grid2 container spacing={2}>
                <Grid2 size={6}>
                  <TextField
                    label="From"
                    type="date"
                    value={draft.oa ?? ''}
                    onChange={e => setDraft(d => ({ ...d, oa: e.target.value }))}
                    fullWidth
                    slotProps={{ inputLabel: { shrink: true } }}
                  />
                </Grid2>
                <Grid2 size={6}>
                  <TextField
                    label="To"
                    type="date"
                    value={draft.ob ?? ''}
                    onChange={e => setDraft(d => ({ ...d, ob: e.target.value }))}
                    fullWidth
                    slotProps={{ inputLabel: { shrink: true } }}
                  />
                </Grid2>
              </Grid2>
            </Grid2>

            <Grid2 size={6}>
              <Typography variant="subtitle2" sx={{ paddingBottom: 2 }}>
                Closed
              </Typography>
              <Grid2 container spacing={2}>
                <Grid2 size={6}>
                  <TextField
                    label="From"
                    type="date"
                    value={draft.ca ?? ''}
                    onChange={e => setDraft(d => ({ ...d, ca: e.target.value }))}
                    fullWidth
                    slotProps={{ inputLabel: { shrink: true } }}
                  />
                </Grid2>
                <Grid2 size={6}>
                  <TextField
                    label="To"
                    type="date"
                    value={draft.cb ?? ''}
                    onChange={e => setDraft(d => ({ ...d, cb: e.target.value }))}
                    fullWidth
                    slotProps={{ inputLabel: { shrink: true } }}
                  />
                </Grid2>
              </Grid2>
            </Grid2>
          </Grid2>
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button color="inherit" onClick={handleReset}>
          Reset
        </Button>
        <Button onClick={onClose}>Cancel</Button>
        <Button variant="contained" onClick={handleApply}>
          Apply
        </Button>
      </DialogActions>
    </Dialog>
  );
}

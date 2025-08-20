/* SOHT2 © Licensed under MIT 2025. */
import { useCallback, useEffect, useMemo, useState } from 'react';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import Stack from '@mui/material/Stack';
import Grid2 from '@mui/material/Grid2';
import Typography from '@mui/material/Typography';

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

export default function HistoryFiltersDialog({
  open,
  value,
  onApply,
  onClose,
}: Readonly<{
  open: boolean;
  value?: HistoryFilters;
  onApply: (filters: HistoryFilters) => void;
  onClose: () => void;
}>) {
  const [draft, setDraft] = useState<HistoryFilters>({});

  useEffect(() => {
    setDraft(value ?? {});
  }, [value, open]);

  const parseArray = useCallback((text: string): string[] => {
    return text
      .split(/[\s,;]+/)
      .map(s => s.trim())
      .filter(Boolean);
  }, []);

  const parseNumberArray = useCallback(
    (text: string): number[] => {
      return parseArray(text)
        .map(x => Number(x))
        .filter(n => !Number.isNaN(n));
    },
    [parseArray]
  );

  const usersText = useMemo(() => (draft.un ?? []).join(', '), [draft.un]);
  const idsText = useMemo(() => (draft.id ?? []).join(', '), [draft.id]);
  const portsText = useMemo(() => (draft.tp ?? []).join(', '), [draft.tp]);

  const handleReset = useCallback(() => setDraft({}), []);

  const handleApply = useCallback(() => {
    // Sanitize and apply
    const cleaned: HistoryFilters = {};
    if (draft.un && draft.un.length) cleaned.un = draft.un;
    if (draft.id && draft.id.length) cleaned.id = draft.id;
    if (draft.ch) cleaned.ch = draft.ch;
    if (draft.th) cleaned.th = draft.th;
    if (draft.tp && draft.tp.length) cleaned.tp = draft.tp;
    if (draft.oa) cleaned.oa = draft.oa;
    if (draft.ob) cleaned.ob = draft.ob;
    if (draft.ca) cleaned.ca = draft.ca;
    if (draft.cb) cleaned.cb = draft.cb;
    onApply(cleaned);
  }, [draft, onApply]);

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth>
      <DialogTitle>History Filters</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Grid2 container spacing={2}>
            <Grid2 size={6}>
              <TextField
                label="Users"
                value={usersText}
                onChange={e => setDraft(d => ({ ...d, un: parseArray(e.target.value) }))}
                helperText="Comma/space separated"
                fullWidth
              />
            </Grid2>
            <Grid2 size={6}>
              <TextField
                label="Connection IDs"
                value={idsText}
                onChange={e => setDraft(d => ({ ...d, id: parseArray(e.target.value) }))}
                helperText="Comma/space separated UUIDs"
                fullWidth
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
              <TextField
                label="Target Ports"
                value={portsText}
                onChange={e => setDraft(d => ({ ...d, tp: parseNumberArray(e.target.value) }))}
                helperText="Comma/space separated numbers"
                fullWidth
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
                    label="After"
                    type="datetime-local"
                    value={draft.oa ?? ''}
                    onChange={e => setDraft(d => ({ ...d, oa: e.target.value }))}
                    fullWidth
                    slotProps={{ inputLabel: { shrink: true } }}
                  />
                </Grid2>
                <Grid2 size={6}>
                  <TextField
                    label="Before"
                    type="datetime-local"
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
                    label="After"
                    type="datetime-local"
                    value={draft.ca ?? ''}
                    onChange={e => setDraft(d => ({ ...d, ca: e.target.value }))}
                    fullWidth
                    slotProps={{ inputLabel: { shrink: true } }}
                  />
                </Grid2>
                <Grid2 size={6}>
                  <TextField
                    label="Before"
                    type="datetime-local"
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

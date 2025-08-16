/* SOHT2 © Licensed under MIT 2025. */
import { useState } from 'react';
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import Chip from '@mui/material/Chip';

export const TARGET_REGEX = /^[a-z0-9.*-]+:[0-9*]+$/;

type AllowedTargetProps = Readonly<{
  label?: string;
  targets: string[];
  onChange: (targets: string[]) => void;
}>;

export default function AllowedTargets({
  label = 'Allowed Target',
  targets,
  onChange,
}: AllowedTargetProps) {
  const [targetInput, setTargetInput] = useState('');
  const [targetError, setTargetError] = useState<string | null>(null);

  const addTarget = () => {
    const value = targetInput.trim();
    if (!value) return;
    if (!TARGET_REGEX.test(value)) {
      setTargetError("Invalid format. Expected something like 'host:123' or '*.host:*'");
      return;
    }
    if (targets.includes(value)) {
      setTargetError('Target already added');
      return;
    }
    onChange([...targets, value]);
    setTargetInput('');
    setTargetError(null);
  };

  const removeTarget = (t: string) => {
    onChange(targets.filter(x => x !== t));
  };

  return (
    <Box>
      <TextField
        label={label}
        placeholder="e.g. host:123 or *.host:*"
        value={targetInput}
        onChange={e => {
          setTargetInput(e.target.value);
          if (targetError) setTargetError(null);
        }}
        onKeyDown={e => {
          if (e.key === 'Enter') {
            e.preventDefault();
            addTarget();
          }
        }}
        error={!!targetError}
        helperText={targetError || 'Press Enter to add target'}
        fullWidth
      />
      {targets.length > 0 && (
        <Box sx={{ mt: 1, display: 'flex', flexWrap: 'wrap', gap: 1 }}>
          {targets.map(t => (
            <Chip key={t} label={t} onDelete={() => removeTarget(t)} />
          ))}
        </Box>
      )}
    </Box>
  );
}

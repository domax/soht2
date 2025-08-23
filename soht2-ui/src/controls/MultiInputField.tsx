/* SOHT2 © Licensed under MIT 2025. */
import { type KeyboardEvent, useCallback, useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import Chip from '@mui/material/Chip';
import InputAdornment from '@mui/material/InputAdornment';
import AddCircleIcon from '@mui/icons-material/AddCircle';
import IconButton from '@mui/material/IconButton';

export default function MultiInputField<T>({
  label,
  values = [],
  valueErrorHint,
  placeholder,
  helperText = 'Press Enter to add value',
  readonly = false,
  valueInputPredicate = () => true,
  stringToValue = (v: string) => v as T,
  valueToString = (v: T) => v as string,
  onChange,
}: Readonly<{
  label?: string;
  values: T[];
  valueErrorHint?: string;
  placeholder?: string;
  helperText?: string;
  readonly?: boolean;
  valueInputPredicate?: (v: string) => boolean;
  stringToValue?: (v: string) => T;
  valueToString?: (v: T) => string;
  onChange?: (values: T[]) => void;
}>) {
  const [valueInput, setValueInput] = useState('');
  const [valueError, setValueError] = useState<string | null>(null);

  const handleAddValue = useCallback(() => {
    const valueString = valueInput.trim();
    if (!valueString) return;
    if (!valueInputPredicate?.(valueString)) {
      setValueError(`${valueErrorHint ?? 'Invalid value format.'}`);
      return;
    }
    const value = stringToValue(valueString);
    if (values.includes(value)) {
      setValueError('Value is already added.');
      return;
    }
    const newValues = [...values, value];
    if (onChange) onChange(newValues);
    setValueInput('');
    setValueError(null);
  }, [valueInput, valueInputPredicate, stringToValue, values, onChange, valueErrorHint]);

  const handleRemoveValue = useCallback(
    (t: T) => {
      const newValues = values.filter(x => x !== t);
      if (onChange) onChange(newValues);
    },
    [onChange, values]
  );

  const handleEnterValue = useCallback(
    (e: KeyboardEvent<HTMLDivElement>) => {
      if (e.key === 'Enter') {
        e.preventDefault();
        handleAddValue();
      }
    },
    [handleAddValue]
  );

  useEffect(() => {
    setValueInput('');
    setValueError(null);
  }, [values]);

  return (
    <Box>
      <TextField
        label={label}
        placeholder={placeholder}
        value={valueInput}
        onChange={e => {
          setValueInput(e.target.value);
          if (valueError) setValueError(null);
        }}
        onKeyDown={handleEnterValue}
        error={!!valueError}
        helperText={valueError ?? helperText}
        fullWidth
        slotProps={{
          input: {
            readOnly: readonly,
            endAdornment: !readonly ? (
              <InputAdornment position="end">
                <IconButton
                  aria-label={'Add value'}
                  onClick={handleAddValue}
                  edge="end"
                  size="small">
                  <AddCircleIcon />
                </IconButton>
              </InputAdornment>
            ) : undefined,
          },
        }}
      />
      {(values?.length ?? []) > 0 && (
        <Box sx={{ mt: 1, display: 'flex', flexWrap: 'wrap', gap: 1 }}>
          {values.map(v => {
            const s = valueToString(v);
            return (
              <Chip
                key={s}
                label={s}
                onDelete={!readonly ? () => handleRemoveValue(v) : undefined}
              />
            );
          })}
        </Box>
      )}
    </Box>
  );
}

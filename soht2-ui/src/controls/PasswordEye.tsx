/* SOHT2 © Licensed under MIT 2025. */
import React from 'react';
import type { TextFieldVariants } from '@mui/material/TextField/TextField';
import TextField from '@mui/material/TextField';
import InputAdornment from '@mui/material/InputAdornment';
import IconButton from '@mui/material/IconButton';
import VisibilityOff from '@mui/icons-material/VisibilityOff';
import Visibility from '@mui/icons-material/Visibility';

type PasswordEyeProps = Readonly<{
  password?: string | null;
  label: string | undefined;
  helperText: string | undefined;
  variant: TextFieldVariants | undefined;
  fullWidth: boolean | undefined;
  margin: 'dense' | 'normal' | 'none' | undefined;
  required: boolean | undefined;
  autoComplete: string | undefined;
  onChange: (password: string) => void;
  onEnter?: () => void | null;
}>;

export default function PasswordEye({
  password,
  label = 'Password',
  helperText,
  variant,
  fullWidth,
  margin,
  required = true,
  autoComplete = 'new-password',
  onChange,
  onEnter,
}: PasswordEyeProps) {
  const [showPassword, setShowPassword] = React.useState(false);

  return (
    <TextField
      label={label}
      value={password}
      helperText={helperText}
      variant={variant}
      fullWidth={fullWidth}
      margin={margin}
      required={required}
      autoComplete={autoComplete}
      type={showPassword ? 'text' : 'password'}
      onChange={e => onChange(e.target.value)}
      onKeyDown={e => {
        if (!!onEnter && e.key === 'Enter') onEnter();
      }}
      slotProps={{
        input: {
          endAdornment: (
            <InputAdornment position="end">
              <IconButton
                aria-label={showPassword ? 'Hide password' : 'Show password'}
                onClick={() => setShowPassword(p => !p)}
                edge="end"
                size="small">
                {showPassword ? <VisibilityOff /> : <Visibility />}
              </IconButton>
            </InputAdornment>
          ),
        },
      }}
    />
  );
}

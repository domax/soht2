import type { MouseEvent } from 'react';
import IconButton from '@mui/material/IconButton';
import MoreVertIcon from '@mui/icons-material/MoreVert';

export default function HeaderMenuButton({
  menuHeaderAnchor,
  handleMenuHeaderOpen,
}: Readonly<{
  menuHeaderAnchor: HTMLElement | null;
  handleMenuHeaderOpen: (e: MouseEvent<HTMLElement>) => void;
}>) {
  return (
    <IconButton
      size="small"
      color="primary"
      aria-label="actions-connections"
      aria-controls={menuHeaderAnchor ? 'connections-header-menu' : undefined}
      aria-haspopup="true"
      onClick={handleMenuHeaderOpen}>
      <MoreVertIcon />
    </IconButton>
  );
}

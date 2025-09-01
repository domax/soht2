import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  base: './',
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          react: ['react', 'react-dom'],
          material: ['@mui/material'],
          dataGrid: ['@mui/x-data-grid'],
          datePickers: ['@mui/x-date-pickers', 'dayjs'],
        },
      },
    },
  },
});

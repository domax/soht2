/* SOHT2 © Licensed under MIT 2025. */
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  base: './',
  build: {
    rollupOptions: {
      output: {
        manualChunks(moduleId) {
          const chunks: Record<string, string[]> = {
            react: ['react', 'react-dom'],
            material: ['@mui/material'],
            dataGrid: ['@mui/x-data-grid'],
            datePickers: ['@mui/x-date-pickers', 'dayjs'],
          };
          for (const [chunk, packages] of Object.entries(chunks)) {
            for (const pkg of packages) {
              if (
                moduleId.includes(`node_modules/${pkg}/`) ||
                moduleId.endsWith(`node_modules/${pkg}`)
              ) {
                return chunk;
              }
            }
          }
          return null;
        },
      },
    },
  },
});

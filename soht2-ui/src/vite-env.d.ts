/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_APP_API_ORIGIN: string;
  // Add other VITE_ prefixed variables here
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

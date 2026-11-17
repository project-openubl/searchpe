/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly PUBLIC_PATH: string;
  readonly BASE_URL: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

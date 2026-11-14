/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_MODE?: 'real' | 'hybrid' | 'mock'
  readonly VITE_API_BASE_URL?: string
  readonly VITE_PROXY_TARGET?: string
}
interface ImportMeta {
  readonly env: ImportMetaEnv
}

import path from 'node:path'
import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Dev: /api được proxy sang BE (mặc định http://localhost:8095) — FE và BE cùng origin, không cần CORS.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: { '@': path.resolve(import.meta.dirname, './src') },
    },
    server: {
      port: 5173,
      host: true,
      proxy: {
        '/api': {
          target: env.VITE_PROXY_TARGET || 'http://localhost:8095',
          changeOrigin: true,
          // Bỏ header Origin: qua proxy là cùng origin, không cần CORS. Nếu giữ, BE từ chối (403) khi
          // Vite tự nhảy sang cổng ngoài whitelist CORS (5176, 5199…) — lỗi kinh điển của BOC (#3).
          configure: (proxy) => {
            proxy.on('proxyReq', (req) => req.removeHeader('origin'))
          },
        },
      },
    },
    build: {
      chunkSizeWarningLimit: 1200,
    },
  }
})

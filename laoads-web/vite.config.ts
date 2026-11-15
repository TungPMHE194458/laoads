import fs from 'node:fs'
import path from 'node:path'
import { defineConfig, loadEnv, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

// Đường dẫn gốc đúng hoa/thường như trên ổ đĩa. Trên Windows, terminal hay mở ở "d:\..." (chữ thường) trong khi
// resolver trả "D:\..." → cùng một file có 2 module id → React / React Router bị đóng gói 2 bản → bản build trang
// trắng ("reading 'useRef'", "useNavigate() may be used only in the context of a <Router>"). Dev server không bị.
const ROOT = fs.realpathSync.native(import.meta.dirname)

/** Chặn build nếu một module bị đóng gói ở 2 nơi (so sánh không phân biệt hoa/thường). */
function noDuplicateModules(): Plugin {
  return {
    name: 'laoads:no-duplicate-modules',
    apply: 'build',
    generateBundle(_, bundle) {
      const seen = new Map<string, string>()
      const dups: string[] = []
      for (const out of Object.values(bundle)) {
        if (out.type !== 'chunk') continue
        for (const id of out.moduleIds) {
          if (id.startsWith('\0')) continue
          const key = id.replace(/\\/g, '/').toLowerCase()
          const where = `${out.fileName} (${id})`
          const prev = seen.get(key)
          if (prev && prev !== where) dups.push(`${prev}  ↔  ${where}`)
          else seen.set(key, where)
        }
      }
      if (dups.length) {
        this.error(`${dups.length} module bị đóng gói 2 lần — bundle sẽ hỏng khi chạy:\n  ${dups.slice(0, 10).join('\n  ')}`)
      }
    },
  }
}

// Dev: /api được proxy sang BE (mặc định http://localhost:8095) — FE và BE cùng origin, không cần CORS.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, ROOT, '')
  return {
    root: ROOT,
    plugins: [react(), tailwindcss(), noDuplicateModules()],
    resolve: {
      alias: { '@': path.join(ROOT, 'src') },
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

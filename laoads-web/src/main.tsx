import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { RouterProvider } from 'react-router-dom'
import { QueryClientProvider } from '@tanstack/react-query'
import { Toaster } from 'sonner'
import '@fontsource-variable/inter'
import '@fontsource-variable/noto-sans-lao'
import './index.css'
import '@/i18n'
import { queryClient } from '@/api/queryClient'
import { TooltipProvider } from '@/components/ui/misc'
import { applyTheme, useUiStore } from '@/stores/ui'
import { router } from './routes'

const API_MODE = (import.meta.env.VITE_API_MODE || 'hybrid') as 'real' | 'hybrid' | 'mock'

/** MSW + dữ liệu mock được tách chunk riêng; chế độ real không tải. */
async function startMocks() {
  if (API_MODE === 'real') return
  const m = await import('@/mocks/browser')
  await m.startMocks(API_MODE)
}

applyTheme(useUiStore.getState().theme)
window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
  if (useUiStore.getState().theme === 'system') applyTheme('system')
})

// MSW phải sẵn sàng trước khi render để request đầu tiên không lọt ra ngoài
startMocks()
  .catch((e) => console.warn('[mocks] không khởi động được MSW', e))
  .finally(() => {
    createRoot(document.getElementById('root')!).render(
      <StrictMode>
        <QueryClientProvider client={queryClient}>
          <TooltipProvider delayDuration={200}>
            <RouterProvider router={router} />
            <Toaster position="top-right" richColors closeButton toastOptions={{ className: 'font-sans' }} />
          </TooltipProvider>
        </QueryClientProvider>
      </StrictMode>,
    )
  })

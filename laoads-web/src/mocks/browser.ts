import { setupWorker } from 'msw/browser'
import { campaignHandlers } from './handlers/campaign'
import { coreHandlers } from './handlers/core'
import { pendingHandlers } from './handlers/pending'

export type ApiMode = 'real' | 'hybrid' | 'mock'

/**
 * - mock:   mock toàn bộ (core + campaign + pending) — chạy UI không cần BE.
 * - hybrid: chỉ mock phần BE chưa có (pending); còn lại đi thẳng BE.
 * - real:   không nạp module này (main.tsx import động).
 */
export async function startMocks(mode: ApiMode) {
  const handlers = mode === 'mock' ? [...coreHandlers, ...campaignHandlers, ...pendingHandlers] : pendingHandlers
  if (handlers.length === 0) {
    // hybrid khi mọi module đã có BE: không cần MSW — gỡ service worker mock cũ (nếu có) để request đi thẳng BE
    const regs = (await navigator.serviceWorker?.getRegistrations()) ?? []
    await Promise.all(regs.filter((r) => r.active?.scriptURL.endsWith('/mockServiceWorker.js')).map((r) => r.unregister()))
    return
  }
  const worker = setupWorker(...handlers)
  await worker.start({ onUnhandledRequest: 'bypass', quiet: true })
}

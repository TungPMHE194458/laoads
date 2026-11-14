import { setupWorker } from 'msw/browser'
import { coreHandlers } from './handlers/core'
import { pendingHandlers } from './handlers/pending'

export type ApiMode = 'real' | 'hybrid' | 'mock'

/**
 * - mock:   mock toàn bộ (core + pending) — chạy UI không cần BE.
 * - hybrid: chỉ mock phần BE đợt 1 chưa có (pending); core đi thẳng BE.
 * - real:   không nạp module này (main.tsx import động).
 */
export async function startMocks(mode: ApiMode) {
  const handlers = mode === 'mock' ? [...coreHandlers, ...pendingHandlers] : pendingHandlers
  const worker = setupWorker(...handlers)
  await worker.start({ onUnhandledRequest: 'bypass', quiet: true })
}

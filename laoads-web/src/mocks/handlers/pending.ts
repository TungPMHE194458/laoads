/*
 * Mock cho endpoint BE CHƯA CÓ — nạp ở cả VITE_API_MODE=mock và hybrid. Khi BE triển khai một module,
 * chuyển handler của nó sang handlers/core.ts hoặc handlers/campaign.ts (chỉ nạp ở mock) để hybrid gọi BE thật.
 * Hiện mọi module FE dùng đều đã có BE.
 */
import type { HttpHandler } from 'msw'

export const pendingHandlers: HttpHandler[] = []

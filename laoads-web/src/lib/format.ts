import { format, parseISO } from 'date-fns'
import i18n, { LOCALE_MAP, type Lang } from '@/i18n'

function locale(): string {
  const l = (i18n.resolvedLanguage ?? 'vi') as Lang
  return LOCALE_MAP[l] ?? 'vi-VN'
}

/** Số nguyên/thực theo locale hiện tại. */
export function fmtNumber(v: number | null | undefined, digits = 0) {
  if (v == null || Number.isNaN(v)) return '—'
  return new Intl.NumberFormat(locale(), { maximumFractionDigits: digits }).format(v)
}

/** Rút gọn: 1.2K, 3.4M */
export function fmtCompact(v: number | null | undefined) {
  if (v == null || Number.isNaN(v)) return '—'
  return new Intl.NumberFormat(locale(), { notation: 'compact', maximumFractionDigits: 1 }).format(v)
}

/** Tỷ lệ 0..1 → "12,3%" */
export function fmtPercent(ratio: number | null | undefined, digits = 1) {
  if (ratio == null || Number.isNaN(ratio)) return '—'
  return new Intl.NumberFormat(locale(), { style: 'percent', maximumFractionDigits: digits }).format(ratio)
}

/** Tiền Kip */
export function fmtKip(v: number | null | undefined) {
  if (v == null || Number.isNaN(v)) return '—'
  return `${fmtNumber(v)} ₭`
}

function toDate(v: string | Date) {
  return typeof v === 'string' ? parseISO(v) : v
}

// Ngày giờ dạng số (dd/MM/yyyy) — dùng chung cho cả 3 ngôn ngữ
export function fmtDate(v: string | Date | null | undefined) {
  if (!v) return '—'
  return format(toDate(v), 'dd/MM/yyyy')
}

export function fmtDateTime(v: string | Date | null | undefined) {
  if (!v) return '—'
  return format(toDate(v), 'dd/MM/yyyy HH:mm')
}

/** "3 ngày trước" — Intl.RelativeTimeFormat hỗ trợ cả vi/en/lo (date-fns không có locale Lào). */
export function fmtRelative(v: string | Date | null | undefined) {
  if (!v) return '—'
  const diff = (toDate(v).getTime() - Date.now()) / 1000
  const rtf = new Intl.RelativeTimeFormat(locale(), { numeric: 'auto' })
  const abs = Math.abs(diff)
  if (abs < 60) return rtf.format(Math.round(diff), 'second')
  if (abs < 3600) return rtf.format(Math.round(diff / 60), 'minute')
  if (abs < 86400) return rtf.format(Math.round(diff / 3600), 'hour')
  if (abs < 86400 * 30) return rtf.format(Math.round(diff / 86400), 'day')
  if (abs < 86400 * 365) return rtf.format(Math.round(diff / (86400 * 30)), 'month')
  return rtf.format(Math.round(diff / (86400 * 365)), 'year')
}

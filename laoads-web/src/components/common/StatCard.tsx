import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { ArrowDownRight, ArrowUpRight, Minus } from 'lucide-react'
import { Skeleton } from '@/components/ui/misc'
import { cn } from '@/lib/utils'
import { fmtNumber } from '@/lib/format'
import type { Metric } from '@/api/types'

/**
 * Thẻ số liệu (stat tile) — số lớn + so sánh kỳ trước.
 * Tăng/giảm luôn có icon + chữ (không chỉ dựa màu).
 */
export function StatCard({
  label,
  value,
  metric,
  icon,
  hint,
  loading,
  className,
  format = fmtNumber,
}: {
  label: string
  value?: React.ReactNode
  metric?: Metric
  icon?: React.ReactNode
  hint?: React.ReactNode
  loading?: boolean
  className?: string
  format?: (n: number) => string
}) {
  const { t } = useTranslation()
  const growth = metric?.growth
  return (
    <div className={cn('relative overflow-hidden rounded-xl border bg-card p-5 shadow-card', className)}>
      <div className="flex items-start justify-between gap-3">
        <p className="text-[13px] font-medium text-muted-foreground">{label}</p>
        {icon && <span className="grid size-9 place-items-center rounded-lg bg-accent text-brand-600 [&_svg]:size-[18px]">{icon}</span>}
      </div>
      <div className="mt-2 text-[28px] leading-none font-semibold tracking-tight">
        {loading ? <Skeleton className="h-7 w-24" /> : (value ?? (metric ? format(metric.total) : '—'))}
      </div>
      <div className="mt-3 flex items-center gap-1.5 text-xs text-muted-foreground">
        {loading ? (
          <Skeleton className="h-3.5 w-32" />
        ) : metric ? (
          growth == null ? (
            <span>{t('stat.noCompare')}</span>
          ) : (
            <>
              <span
                className={cn(
                  'inline-flex items-center gap-0.5 rounded-md px-1.5 py-0.5 font-medium tabular',
                  growth > 0 && 'bg-success-soft text-success',
                  growth < 0 && 'bg-danger-soft text-danger',
                  growth === 0 && 'bg-muted text-muted-foreground',
                )}
              >
                {growth > 0 ? <ArrowUpRight className="size-3.5" /> : growth < 0 ? <ArrowDownRight className="size-3.5" /> : <Minus className="size-3.5" />}
                {growth > 0 ? t('stat.up') : growth < 0 ? t('stat.down') : t('stat.flat')} {Math.abs(growth).toFixed(1)}%
              </span>
              <span>{t('stat.vsPrev')}</span>
            </>
          )
        ) : (
          hint
        )}
      </div>
    </div>
  )
}

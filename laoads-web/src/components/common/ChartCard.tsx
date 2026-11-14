import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { BarChart3, Table2 } from 'lucide-react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/misc'
import { cn } from '@/lib/utils'

export interface TableView {
  columns: string[]
  rows: (string | number)[][]
}

/**
 * Khung biểu đồ: tiêu đề + nút chuyển Biểu đồ ↔ Bảng.
 * Bảng là "relief" bắt buộc cho các màu series dưới 3:1 trên nền sáng (dataviz validator WARN).
 */
export function ChartCard({
  title,
  description,
  actions,
  table,
  loading,
  height = 280,
  children,
  className,
}: {
  title: React.ReactNode
  description?: React.ReactNode
  actions?: React.ReactNode
  table?: TableView
  loading?: boolean
  height?: number
  children: React.ReactNode
  className?: string
}) {
  const { t } = useTranslation()
  const [mode, setMode] = React.useState<'chart' | 'table'>('chart')
  return (
    <Card className={className}>
      <CardHeader>
        <div className="min-w-0">
          <CardTitle>{title}</CardTitle>
          {description && <CardDescription>{description}</CardDescription>}
        </div>
        <div className="flex items-center gap-2">
          {actions}
          {table && (
            <div className="flex rounded-lg border p-0.5" role="group" aria-label={t('chart.viewMode')}>
              {(['chart', 'table'] as const).map((m) => (
                <button
                  key={m}
                  type="button"
                  aria-pressed={mode === m}
                  title={t(`chart.${m}`)}
                  onClick={() => setMode(m)}
                  className={cn(
                    'grid size-7 cursor-pointer place-items-center rounded-md text-muted-foreground transition',
                    mode === m ? 'bg-muted text-foreground' : 'hover:text-foreground',
                  )}
                >
                  {m === 'chart' ? <BarChart3 className="size-4" /> : <Table2 className="size-4" />}
                </button>
              ))}
            </div>
          )}
        </div>
      </CardHeader>
      <CardContent className="pt-4">
        {loading ? (
          <Skeleton style={{ height }} className="w-full rounded-lg" />
        ) : mode === 'table' && table ? (
          <div className="overflow-auto scrollbar-thin" style={{ maxHeight: height }}>
            <table className="w-full text-sm">
              <thead className="sticky top-0 bg-card">
                <tr className="border-b">
                  {table.columns.map((c, i) => (
                    <th key={c} className={cn('px-3 py-2 text-xs font-medium text-muted-foreground', i === 0 ? 'text-left' : 'text-right')}>
                      {c}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {table.rows.map((r, i) => (
                  <tr key={i} className="border-b last:border-0">
                    {r.map((v, j) => (
                      <td key={j} className={cn('px-3 py-2 tabular', j === 0 ? 'text-left' : 'text-right')}>
                        {v}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div style={{ height }}>{children}</div>
        )}
      </CardContent>
    </Card>
  )
}

/** Legend dùng chung cho biểu đồ ≥ 2 series: chấm màu + nhãn chữ (chữ dùng màu text, không dùng màu series). */
export function ChartLegend({ items }: { items: { label: string; color: string; dashed?: boolean }[] }) {
  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-muted-foreground">
      {items.map((i) => (
        <span key={i.label} className="inline-flex items-center gap-1.5">
          <span className="inline-block h-[3px] w-3.5 rounded-full" style={{ background: i.color }} />
          {i.label}
        </span>
      ))}
    </div>
  )
}

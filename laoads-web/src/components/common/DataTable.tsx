import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { ChevronLeft, ChevronRight, Inbox } from 'lucide-react'
import type { PageMeta } from '@/api/types'
import { Button } from '@/components/ui/button'
import { Checkbox, Skeleton } from '@/components/ui/misc'
import { Select } from '@/components/ui/select'
import { cn } from '@/lib/utils'
import { fmtNumber } from '@/lib/format'

export interface Column<T> {
  key: string
  header: React.ReactNode
  cell: (row: T, index: number) => React.ReactNode
  className?: string
  headerClassName?: string
  align?: 'left' | 'right' | 'center'
  width?: string
}

interface Props<T> {
  columns: Column<T>[]
  rows: T[] | undefined
  rowKey: (row: T) => string | number
  loading?: boolean
  error?: React.ReactNode
  empty?: React.ReactNode
  onRowClick?: (row: T) => void
  meta?: PageMeta
  onPageChange?: (page: number) => void
  onPageSizeChange?: (size: number) => void
  /** Bật cột checkbox chọn nhiều dòng */
  selectable?: boolean
  selectedIds?: (string | number)[]
  onSelectionChange?: (ids: (string | number)[]) => void
  isRowSelectable?: (row: T) => boolean
  toolbar?: React.ReactNode
  className?: string
  dense?: boolean
}

export function DataTable<T>({
  columns,
  rows,
  rowKey,
  loading,
  error,
  empty,
  onRowClick,
  meta,
  onPageChange,
  onPageSizeChange,
  selectable,
  selectedIds = [],
  onSelectionChange,
  isRowSelectable = () => true,
  toolbar,
  className,
  dense,
}: Props<T>) {
  const { t } = useTranslation()
  const data = rows ?? []
  const selectableRows = data.filter(isRowSelectable)
  const allSelected = selectableRows.length > 0 && selectableRows.every((r) => selectedIds.includes(rowKey(r)))
  const someSelected = !allSelected && selectableRows.some((r) => selectedIds.includes(rowKey(r)))

  const toggleAll = () => {
    if (!onSelectionChange) return
    onSelectionChange(allSelected ? [] : selectableRows.map(rowKey))
  }
  const toggleOne = (id: string | number) => {
    if (!onSelectionChange) return
    onSelectionChange(selectedIds.includes(id) ? selectedIds.filter((x) => x !== id) : [...selectedIds, id])
  }
  const align = (a?: Column<T>['align']) => (a === 'right' ? 'text-right' : a === 'center' ? 'text-center' : 'text-left')
  const colCount = columns.length + (selectable ? 1 : 0)
  const py = dense ? 'py-2' : 'py-3'

  return (
    <div className={cn('overflow-hidden rounded-xl border bg-card shadow-card', className)}>
      {toolbar && <div className="flex flex-wrap items-center gap-2 border-b px-4 py-3">{toolbar}</div>}
      <div className="overflow-x-auto scrollbar-thin">
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr className="border-b bg-muted/50">
              {selectable && (
                <th className="w-10 px-4 py-2.5">
                  <Checkbox
                    aria-label={t('table.selectAll')}
                    checked={allSelected ? true : someSelected ? 'indeterminate' : false}
                    onCheckedChange={toggleAll}
                  />
                </th>
              )}
              {columns.map((c) => (
                <th
                  key={c.key}
                  style={c.width ? { width: c.width } : undefined}
                  className={cn(
                    'px-4 py-2.5 text-xs font-medium tracking-wide whitespace-nowrap text-muted-foreground uppercase',
                    align(c.align),
                    c.headerClassName,
                  )}
                >
                  {c.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading &&
              Array.from({ length: 5 }).map((_, i) => (
                <tr key={`sk-${i}`} className="border-b last:border-0">
                  {Array.from({ length: colCount }).map((__, j) => (
                    <td key={j} className={cn('px-4', py)}>
                      <Skeleton className="h-4 w-full max-w-40" />
                    </td>
                  ))}
                </tr>
              ))}
            {!loading && error && (
              <tr>
                <td colSpan={colCount} className="px-4 py-12 text-center text-sm text-danger">
                  {error}
                </td>
              </tr>
            )}
            {!loading && !error && data.length === 0 && (
              <tr>
                <td colSpan={colCount} className="px-4 py-14">
                  {empty ?? (
                    <div className="flex flex-col items-center gap-2 text-muted-foreground">
                      <span className="grid size-11 place-items-center rounded-full bg-muted">
                        <Inbox className="size-5" />
                      </span>
                      <span className="text-sm">{t('table.empty')}</span>
                    </div>
                  )}
                </td>
              </tr>
            )}
            {!loading &&
              !error &&
              data.map((row, i) => {
                const id = rowKey(row)
                const selected = selectedIds.includes(id)
                return (
                  <tr
                    key={id}
                    onClick={onRowClick ? () => onRowClick(row) : undefined}
                    className={cn(
                      'border-b transition-colors last:border-0',
                      onRowClick && 'cursor-pointer hover:bg-muted/40',
                      selected && 'bg-accent/50',
                    )}
                  >
                    {selectable && (
                      <td className={cn('px-4', py)} onClick={(e) => e.stopPropagation()}>
                        <Checkbox
                          aria-label={t('table.selectRow')}
                          checked={selected}
                          disabled={!isRowSelectable(row)}
                          onCheckedChange={() => toggleOne(id)}
                        />
                      </td>
                    )}
                    {columns.map((c) => (
                      <td key={c.key} className={cn('px-4 align-middle', py, align(c.align), c.className)}>
                        {c.cell(row, i)}
                      </td>
                    ))}
                  </tr>
                )
              })}
          </tbody>
        </table>
      </div>
      {meta && meta.totalItems > 0 && (
        <Pagination meta={meta} onPageChange={onPageChange} onPageSizeChange={onPageSizeChange} />
      )}
    </div>
  )
}

export function Pagination({
  meta,
  onPageChange,
  onPageSizeChange,
}: {
  meta: PageMeta
  onPageChange?: (p: number) => void
  onPageSizeChange?: (s: number) => void
}) {
  const { t } = useTranslation()
  const from = meta.currentPage * meta.pageSize + 1
  const to = Math.min(meta.totalItems, from + meta.pageSize - 1)
  const pages = buildPages(meta.currentPage, meta.totalPages)
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 border-t px-4 py-3 text-[13px] text-muted-foreground">
      <span className="tabular">
        {t('table.showing', { from: fmtNumber(from), to: fmtNumber(to), total: fmtNumber(meta.totalItems) })}
      </span>
      <div className="flex items-center gap-1">
        {onPageSizeChange && (
          <div className="mr-2 w-28">
            <Select
              size="sm"
              aria-label={t('table.pageSize')}
              value={String(meta.pageSize)}
              onValueChange={(v) => onPageSizeChange(Number(v))}
              options={[10, 20, 50, 100].map((n) => ({ value: String(n), label: t('table.perPage', { n }) }))}
            />
          </div>
        )}
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label={t('table.prev')}
          disabled={meta.currentPage <= 0}
          onClick={() => onPageChange?.(meta.currentPage - 1)}
        >
          <ChevronLeft />
        </Button>
        {pages.map((p, i) =>
          p === -1 ? (
            <span key={`e${i}`} className="px-1">
              …
            </span>
          ) : (
            <button
              key={p}
              onClick={() => onPageChange?.(p)}
              className={cn(
                'tabular h-8 min-w-8 cursor-pointer rounded-md px-2 text-[13px] transition',
                p === meta.currentPage ? 'bg-accent font-semibold text-accent-foreground' : 'hover:bg-muted',
              )}
            >
              {p + 1}
            </button>
          ),
        )}
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label={t('table.next')}
          disabled={meta.currentPage >= meta.totalPages - 1}
          onClick={() => onPageChange?.(meta.currentPage + 1)}
        >
          <ChevronRight />
        </Button>
      </div>
    </div>
  )
}

function buildPages(current: number, total: number): number[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i)
  const set = new Set([0, total - 1, current - 1, current, current + 1].filter((p) => p >= 0 && p < total))
  const sorted = [...set].sort((a, b) => a - b)
  const out: number[] = []
  sorted.forEach((p, i) => {
    if (i > 0 && p - sorted[i - 1] > 1) out.push(-1)
    out.push(p)
  })
  return out
}

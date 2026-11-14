/*
 * Biểu đồ dùng chung — theo skill dataviz:
 *  - 1 trục Y (không dual-axis); chuỗi đơn dùng màu thương hiệu, không cần legend (tiêu đề đã gọi tên).
 *  - Màu kênh cố định theo entity (CHANNEL_META), không theo thứ hạng.
 *  - Nét 2px, lưới mờ, tooltip crosshair, chữ dùng màu text (không dùng màu series).
 */
import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip as RTooltip, XAxis, YAxis } from 'recharts'
import type { Channel } from '@/api/types'
import { CHANNEL_META } from '@/components/common/status'
import { Tooltip } from '@/components/ui/misc'
import { fmtCompact, fmtNumber, fmtPercent } from '@/lib/format'
import { cn } from '@/lib/utils'

const axisTick = { fill: 'var(--chart-axis)', fontSize: 12 }

function ChartTooltip({ active, payload, label, valueLabel, labelFormat }: { active?: boolean; payload?: { value: number }[]; label?: string; valueLabel: string; labelFormat?: (l: string) => string }) {
  if (!active || !payload?.length) return null
  return (
    <div className="rounded-lg border bg-popover px-3 py-2 text-xs shadow-pop">
      <div className="text-muted-foreground">{labelFormat && label ? labelFormat(label) : label}</div>
      <div className="mt-1 flex items-center gap-2">
        <span className="size-2 rounded-full" style={{ background: 'var(--series-brand)' }} />
        <span className="text-foreground/80">{valueLabel}</span>
        <span className="ml-auto pl-4 font-semibold text-foreground tabular">{fmtNumber(payload[0].value)}</span>
      </div>
    </div>
  )
}

/** Area chart một chuỗi (màu cam thương hiệu). */
export function TrendArea({
  data,
  xKey,
  yKey,
  valueLabel,
  xFormat,
}: {
  data: object[]
  xKey: string
  yKey: string
  valueLabel: string
  xFormat?: (v: string) => string
}) {
  const id = React.useId().replace(/:/g, '')
  return (
    <ResponsiveContainer width="100%" height="100%">
      <AreaChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -8 }}>
        <defs>
          <linearGradient id={`fill-${id}`} x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="var(--series-brand)" stopOpacity={0.22} />
            <stop offset="100%" stopColor="var(--series-brand)" stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid vertical={false} stroke="var(--chart-grid)" />
        <XAxis dataKey={xKey} tickLine={false} axisLine={false} tick={axisTick} tickFormatter={xFormat} minTickGap={24} dy={6} />
        <YAxis tickLine={false} axisLine={false} tick={axisTick} tickFormatter={(v: number) => fmtCompact(v)} width={52} />
        <RTooltip cursor={{ stroke: 'var(--chart-axis)', strokeDasharray: '3 3' }} content={<ChartTooltip valueLabel={valueLabel} labelFormat={xFormat} />} />
        <Area
          type="monotone"
          dataKey={yKey}
          stroke="var(--series-brand)"
          strokeWidth={2}
          fill={`url(#fill-${id})`}
          activeDot={{ r: 5, strokeWidth: 2, stroke: 'var(--card)', fill: 'var(--series-brand)' }}
          isAnimationActive={false}
        />
      </AreaChart>
    </ResponsiveContainer>
  )
}

/** Thanh phân đoạn (share theo kênh) — legend + nhãn % trực tiếp; khe 2px giữa các đoạn. */
export function ChannelShareBar({ items }: { items: { channel: Channel; value: number }[] }) {
  const { t } = useTranslation()
  const total = items.reduce((s, i) => s + i.value, 0) || 1
  return (
    <div>
      <div className="flex h-3 w-full gap-[2px] overflow-hidden rounded-full">
        {items.map((i) => (
          <Tooltip key={i.channel} content={`${t(`channel.${i.channel}`)}: ${fmtNumber(i.value)} (${fmtPercent(i.value / total)})`}>
            <div className="h-full first:rounded-l-full last:rounded-r-full" style={{ width: `${(i.value / total) * 100}%`, background: CHANNEL_META[i.channel].color }} />
          </Tooltip>
        ))}
      </div>
      <ul className="mt-4 grid grid-cols-2 gap-x-4 gap-y-2.5">
        {items.map((i) => {
          const M = CHANNEL_META[i.channel]
          return (
            <li key={i.channel} className="flex items-center gap-2 text-[13px]">
              <span className="size-2.5 shrink-0 rounded-sm" style={{ background: M.color }} />
              <span className="text-foreground/80">{t(`channel.${i.channel}`)}</span>
              <span className="ml-auto font-semibold tabular">{fmtPercent(i.value / total, 0)}</span>
            </li>
          )
        })}
      </ul>
    </div>
  )
}

/** Phễu chuyển đổi — độ dài thanh mã hoá giá trị, 1 màu; nhãn số + % so với bước đầu. */
export function FunnelBars({ steps }: { steps: { label: string; value: number }[] }) {
  const max = Math.max(1, ...steps.map((s) => s.value))
  const first = steps[0]?.value || 1
  return (
    <ul className="grid gap-3">
      {steps.map((s, i) => (
        <li key={s.label} className="grid grid-cols-[120px_1fr_auto] items-center gap-3 text-[13px] sm:grid-cols-[140px_1fr_auto]">
          <span className="truncate text-muted-foreground">{s.label}</span>
          <Tooltip content={`${s.label}: ${fmtNumber(s.value)}`}>
            <div className="h-7 rounded-md bg-muted/70">
              <div
                className={cn('h-full rounded-md transition-[width] duration-500')}
                style={{ width: `${Math.max(1.5, (s.value / max) * 100)}%`, background: 'var(--series-brand)', opacity: 1 - i * 0.11 }}
              />
            </div>
          </Tooltip>
          <span className="w-28 text-right tabular">
            <span className="font-semibold">{fmtCompact(s.value)}</span>
            {i > 0 && <span className="ml-1.5 text-xs text-muted-foreground">{fmtPercent(s.value / first)}</span>}
          </span>
        </li>
      ))}
    </ul>
  )
}

/** Thanh tỉ lệ nhỏ trong bảng. */
export function RateBar({ value, color = 'var(--series-brand)' }: { value: number; color?: string }) {
  return (
    <div className="flex items-center gap-2">
      <div className="h-1.5 w-20 overflow-hidden rounded-full bg-muted">
        <div className="h-full rounded-full" style={{ width: `${Math.min(100, value * 100)}%`, background: color }} />
      </div>
      <span className="w-12 text-right text-[13px] tabular">{fmtPercent(value)}</span>
    </div>
  )
}

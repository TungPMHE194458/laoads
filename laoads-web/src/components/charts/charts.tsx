/*
 * Biểu đồ dùng chung — theo skill dataviz:
 *  - 1 trục Y (không dual-axis); chuỗi đơn dùng màu thương hiệu, không cần legend (tiêu đề đã gọi tên).
 *  - Màu kênh cố định theo entity (CHANNEL_META), không theo thứ hạng.
 *  - Nét 2px, lưới mờ, tooltip crosshair, chữ dùng màu text (không dùng màu series).
 */
import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { Area, AreaChart, Bar, BarChart, CartesianGrid, Cell, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip as RTooltip, XAxis, YAxis } from 'recharts'
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

export interface SeriesDef {
  key: string
  label: string
  color: string
}

/** Chú thích chuỗi: chấm màu + nhãn màu chữ; bấm để ẩn / hiện chuỗi (giữ nguyên màu theo entity). */
function SeriesLegend({ series, hidden, onToggle, marker = 'line' }: { series: SeriesDef[]; hidden?: Set<string>; onToggle?: (key: string) => void; marker?: 'line' | 'dot' }) {
  return (
    <ul className="mt-3 flex flex-wrap justify-center gap-x-4 gap-y-1.5 text-[13px]">
      {series.map((s) => {
        const off = hidden?.has(s.key)
        const mark = <span className={marker === 'line' ? 'h-[2px] w-4 rounded-full' : 'size-2.5 rounded-full'} style={{ background: s.color }} />
        return (
          <li key={s.key}>
            {onToggle ? (
              <button type="button" aria-pressed={!off} onClick={() => onToggle(s.key)} className={cn('flex cursor-pointer items-center gap-2 rounded px-1 text-foreground/80 hover:text-foreground', off && 'opacity-40')}>
                {mark}
                {s.label}
              </button>
            ) : (
              <span className="flex items-center gap-2 text-foreground/80">
                {mark}
                {s.label}
              </span>
            )}
          </li>
        )
      })}
    </ul>
  )
}

function MultiTooltip({
  active,
  payload,
  label,
  labelFormat,
  valueFormat,
}: {
  active?: boolean
  payload?: { dataKey?: string | number; name?: string; value?: number | null; color?: string }[]
  label?: string
  labelFormat?: (l: string) => string
  valueFormat: (v: number) => string
}) {
  if (!active || !payload?.length) return null
  return (
    <div className="rounded-lg border bg-popover px-3 py-2 text-xs shadow-pop">
      <div className="text-muted-foreground">{labelFormat && label ? labelFormat(label) : label}</div>
      {payload.map((p) => (
        <div key={String(p.dataKey)} className="mt-1 flex items-center gap-2">
          <span className="size-2 rounded-full" style={{ background: p.color }} />
          <span className="text-foreground/80">{p.name}</span>
          <span className="ml-auto pl-4 font-semibold text-foreground tabular">{p.value == null ? '—' : valueFormat(p.value)}</span>
        </div>
      ))}
    </div>
  )
}

/**
 * Biểu đồ đường nhiều chỉ số trên một trục (SRS 3.2.8.1): legend luôn hiện, bấm legend để ẩn chuỗi lớn
 * (vd "Đã gửi") khi muốn xem rõ chuỗi nhỏ; tooltip liệt kê mọi chỉ số tại mốc.
 */
export function MetricLines({ data, xKey, series, xFormat }: { data: object[]; xKey: string; series: SeriesDef[]; xFormat?: (v: string) => string }) {
  const [hidden, setHidden] = React.useState<Set<string>>(new Set())
  const toggle = (k: string) =>
    setHidden((h) => {
      const n = new Set(h)
      if (n.has(k)) n.delete(k)
      else if (n.size < series.length - 1) n.add(k)
      return n
    })
  return (
    <div className="flex h-full flex-col">
      <div className="min-h-0 flex-1">
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -8 }}>
            <CartesianGrid vertical={false} stroke="var(--chart-grid)" />
            <XAxis dataKey={xKey} tickLine={false} axisLine={false} tick={axisTick} tickFormatter={xFormat} minTickGap={24} dy={6} />
            <YAxis tickLine={false} axisLine={false} tick={axisTick} tickFormatter={(v: number) => fmtCompact(v)} width={52} allowDecimals={false} />
            <RTooltip cursor={{ stroke: 'var(--chart-axis)', strokeDasharray: '3 3' }} content={<MultiTooltip labelFormat={xFormat} valueFormat={fmtNumber} />} />
            {series
              .filter((s) => !hidden.has(s.key))
              .map((s) => (
                <Line
                  key={s.key}
                  type="monotone"
                  dataKey={s.key}
                  name={s.label}
                  stroke={s.color}
                  strokeWidth={2}
                  dot={false}
                  activeDot={{ r: 5, strokeWidth: 2, stroke: 'var(--card)', fill: s.color }}
                  isAnimationActive={false}
                />
              ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
      <SeriesLegend series={series} hidden={hidden} onToggle={toggle} />
    </div>
  )
}

/**
 * Cột nhóm (SRS 3.2.8.3): mỗi nhóm trên trục X là một chỉ số tỷ lệ, mỗi cột là một kênh (màu cố định theo kênh).
 * Giá trị null = kênh không đo được chỉ số đó (vd SMS không có lượt mở) → không vẽ cột.
 */
export function GroupedRateBars({ data, xKey, series }: { data: object[]; xKey: string; series: SeriesDef[] }) {
  return (
    <div className="flex h-full flex-col">
      <div className="min-h-0 flex-1">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -8 }} barGap={2} barCategoryGap="24%">
            <CartesianGrid vertical={false} stroke="var(--chart-grid)" />
            <XAxis dataKey={xKey} tickLine={false} axisLine={false} tick={axisTick} dy={6} interval={0} />
            <YAxis tickLine={false} axisLine={false} tick={axisTick} tickFormatter={(v: number) => fmtPercent(v, 0)} width={52} domain={[0, (max: number) => (max > 0 ? Math.min(1, Math.max(0.05, max * 1.25)) : 1)]} />
            <RTooltip cursor={{ fill: 'var(--muted)', opacity: 0.5 }} content={<MultiTooltip valueFormat={(v) => fmtPercent(v)} />} />
            {series.map((s) => (
              <Bar key={s.key} dataKey={s.key} name={s.label} fill={s.color} radius={[4, 4, 0, 0]} maxBarSize={28} isAnimationActive={false} />
            ))}
          </BarChart>
        </ResponsiveContainer>
      </div>
      <SeriesLegend series={series} marker="dot" />
    </div>
  )
}

/**
 * Donut tỷ lệ (SRS 3.2.8.4–6): phần màu = tỷ lệ, phần còn lại màu nền; giữa vòng là tổng (Total Sent / Clicks / Reg),
 * bên cạnh là "nhãn: số (tỷ lệ)" và chú thích màu.
 */
export function RateDonut({
  rate,
  count,
  centerValue,
  centerLabel,
  label,
  color = 'var(--series-brand)',
}: {
  rate: number
  count: number
  centerValue: number
  centerLabel: string
  label: string
  color?: string
}) {
  const v = Math.max(0, Math.min(1, rate))
  const data = [
    { name: 'value', value: v },
    { name: 'rest', value: 1 - v },
  ]
  return (
    <div className="flex flex-col items-center gap-3 sm:flex-row sm:justify-center sm:gap-6">
      <div className="relative size-40 shrink-0">
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={data} dataKey="value" innerRadius="72%" outerRadius="100%" startAngle={90} endAngle={-270} stroke="var(--card)" strokeWidth={2} isAnimationActive={false}>
              <Cell fill={color} />
              <Cell fill="var(--muted)" />
            </Pie>
          </PieChart>
        </ResponsiveContainer>
        <div className="pointer-events-none absolute inset-0 grid place-content-center text-center">
          <div className="text-xl font-semibold tabular">{fmtCompact(centerValue)}</div>
          <div className="text-[11px] text-muted-foreground">{centerLabel}</div>
        </div>
      </div>
      <div className="text-center sm:text-left">
        <div className="flex items-center justify-center gap-2 text-[13px] text-muted-foreground sm:justify-start">
          <span className="size-2.5 rounded-full" style={{ background: color }} />
          {label}
        </div>
        <div className="mt-1 text-lg font-semibold tabular">
          {fmtNumber(count)} <span className="text-muted-foreground">({fmtPercent(v)})</span>
        </div>
      </div>
    </div>
  )
}

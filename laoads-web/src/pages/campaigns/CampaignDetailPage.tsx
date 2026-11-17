import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useParams } from 'react-router-dom'
import {
  AlertOctagon,
  Ban,
  CalendarClock,
  CheckCircle2,
  Copy,
  FlaskConical,
  Goal,
  History,
  Info,
  Lock,
  Megaphone,
  Pause,
  Pencil,
  Play,
  Rocket,
  RotateCw,
  Send,
  Settings2,
  Users2,
  Wallet,
} from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { ActionGroup, CampaignDetail, Channel } from '@/api/types'
import { useCampaign, useCampaignReport } from '@/features/campaign/api'
import { useCan } from '@/features/auth/permissions'
import { PageHeader } from '@/components/common/PageHeader'
import { ChartCard } from '@/components/common/ChartCard'
import { CampaignStatusBadge, CHANNEL_META } from '@/components/common/status'
import { FunnelBars, GroupedRateBars, MetricLines, RateDonut } from '@/components/charts/charts'
import { EmptyState } from '@/components/common/EmptyState'
import { Button } from '@/components/ui/button'
import { ConversionImport } from './ConversionImport'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton, Tabs, TabsContent, TabsList, TabsTrigger, Tooltip } from '@/components/ui/misc'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { fmtCompact, fmtDate, fmtDateTime, fmtKip, fmtNumber, fmtPercent } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useCampaignOps } from './campaignActions'
import { actionIcon } from './wizard/actionMeta'

function Kpi({ label, value, sub }: { label: string; value: React.ReactNode; sub?: React.ReactNode }) {
  return (
    <div className="rounded-xl border bg-card p-4 shadow-card">
      <div className="text-xs font-medium text-muted-foreground">{label}</div>
      <div className="mt-1.5 text-[22px] leading-tight font-semibold tabular">{value}</div>
      {sub && <div className="mt-1 text-xs text-muted-foreground">{sub}</div>}
    </div>
  )
}

/** Biểu tượng (i) cạnh tiêu đề: giải thích công thức chỉ số (SRS 3.2.8.x). */
function InfoTip({ text }: { text: string }) {
  return (
    <Tooltip content={<span className="block max-w-72 text-xs">{text}</span>}>
      <button type="button" className="inline-grid cursor-help place-items-center text-muted-foreground hover:text-foreground" aria-label={text}>
        <Info className="size-3.5" />
      </button>
    </Tooltip>
  )
}

function TitleWithInfo({ title, info }: { title: string; info: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      {title}
      <InfoTip text={info} />
    </span>
  )
}

const METRIC_SERIES = [
  { key: 'sent', color: 'var(--metric-sent)' },
  { key: 'opens', color: 'var(--metric-opens)' },
  { key: 'clicks', color: 'var(--metric-clicks)' },
  { key: 'conversions', color: 'var(--metric-conversions)' },
] as const

const ratio = (a: number, b: number) => (b > 0 ? a / b : 0)
const dateOnly = (iso: string | null | undefined) => (iso ? new Date(iso).toLocaleDateString('sv-SE') : '')

/** Báo cáo chiến dịch — SRS 3.2.8.1–3.2.8.7. */
function ReportTab({ c }: { c: CampaignDetail }) {
  const { t } = useTranslation()
  const can = useCan()
  const [bucket, setBucket] = React.useState<'DAY' | 'WEEK' | 'MONTH'>('DAY')
  // SRS 3.2.8.1: khoảng ngày — mặc định từ ngày bắt đầu chiến dịch đến hôm nay (giờ Lào)
  const [range, setRange] = React.useState(() => ({ from: dateOnly(c.startAt ?? c.createdAt), to: new Date().toLocaleDateString('sv-SE') }))
  const [channel, setChannel] = React.useState('')
  const [donutChannel, setDonutChannel] = React.useState<Channel | ''>('')
  // Đã chạy: đang chạy / tạm dừng / hoàn thành, hoặc đã huỷ sau khi chạy (lịch sử có START)
  const started = ['RUNNING', 'PAUSED', 'COMPLETED'].includes(c.status) || c.history.some((h) => h.action === 'START')
  const allowed = can('REPORT_VIEW')
  const r = useCampaignReport(c.id, { bucket, from: range.from, to: range.to, channel }, started && allowed)

  if (!allowed)
    return (
      <Card>
        <EmptyState icon={<Lock />} title={t('status.forbiddenTitle')} description={t('campaign.detail.noReportPermission')} />
      </Card>
    )
  if (!started)
    return (
      <Card>
        <EmptyState icon={<CalendarClock />} title={t('campaign.detail.notStarted')} description={t('campaign.detail.notStartedDesc')} />
      </Card>
    )
  if (r.isError)
    return (
      <Card>
        <EmptyState
          icon={<AlertOctagon />}
          title={t('campaign.detail.reportError')}
          description={errorMessage(r.error)}
          action={
            <Button variant="outline" onClick={() => r.refetch()}>
              <RotateCw />
              {t('common.retry')}
            </Button>
          }
        />
      </Card>
    )

  const tot = r.data?.totals
  const channels = r.data?.channels ?? []
  const series = METRIC_SERIES.map((m) => ({ ...m, label: t(`metric.${m.key}`) }))
  const chSeries = channels.map((ch) => ({ key: ch.channel, label: t(`channel.${ch.channel}`), color: CHANNEL_META[ch.channel].color }))
  // Tỷ lệ tương tác theo kênh (3.2.8.3): mở / phản hồi (click) / đăng ký; SMS không đo lượt mở → null
  const interaction = [
    { metric: t('campaign.detail.rate.open'), ...Object.fromEntries(channels.map((ch) => [ch.channel, ch.channel === 'SMS' ? null : ratio(ch.opened, ch.delivered)])) },
    { metric: t('campaign.detail.rate.click'), ...Object.fromEntries(channels.map((ch) => [ch.channel, ratio(ch.clicked, ch.delivered)])) },
    { metric: t('campaign.detail.rate.signup'), ...Object.fromEntries(channels.map((ch) => [ch.channel, ratio(ch.converted, ch.sent)])) },
  ]
  // Donut 3.2.8.4–6: mặc định Email nếu chiến dịch có gửi Email, không thì kênh đầu tiên
  const dCh = (donutChannel && channels.find((x) => x.channel === donutChannel)) || channels.find((x) => x.channel === 'EMAIL') || channels[0]
  const dName = dCh ? t(`channel.${dCh.channel}`) : ''
  const noGoal = c.goal.mode === 'NONE'
  const groups = r.data?.groups ?? []

  return (
    <div className="grid gap-6">
      {/* Bộ lọc thời gian (3.2.8.1 #1, #2) */}
      <div className="flex flex-wrap items-center justify-end gap-2">
        <div className="flex rounded-lg border bg-card p-0.5 text-xs" role="group" aria-label={t('campaign.detail.bucketLabel')}>
          {(['DAY', 'WEEK', 'MONTH'] as const).map((b) => (
            <button key={b} aria-pressed={bucket === b} onClick={() => setBucket(b)} className={cn('cursor-pointer rounded-md px-2.5 py-1 transition', bucket === b ? 'bg-accent font-medium text-accent-foreground' : 'text-muted-foreground hover:text-foreground')}>
              {t(`campaign.detail.bucket.${b}`)}
            </button>
          ))}
        </div>
        <div className="flex items-center gap-1.5 rounded-lg border bg-card px-2 py-0.5 text-xs">
          <Input type="date" aria-label={t('campaign.detail.rangeFrom')} className="h-7 w-36 border-0 px-1 text-xs shadow-none" value={range.from} max={range.to || undefined} onChange={(e) => e.target.value && setRange((x) => ({ ...x, from: e.target.value }))} />
          <span className="text-muted-foreground">–</span>
          <Input type="date" aria-label={t('campaign.detail.rangeTo')} className="h-7 w-36 border-0 px-1 text-xs shadow-none" value={range.to} min={range.from || undefined} onChange={(e) => e.target.value && setRange((x) => ({ ...x, to: e.target.value }))} />
        </div>
      </div>

      <div className="grid gap-4 sm:grid-cols-3 xl:grid-cols-6">
        {r.isLoading || !tot ? (
          Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-[92px] rounded-xl" />)
        ) : (
          <>
            <Kpi label={t('metric.target')} value={fmtCompact(tot.target)} />
            <Kpi label={t('metric.sent')} value={fmtCompact(tot.sent)} sub={t('metric.failedN', { n: fmtNumber(tot.failed) })} />
            <Kpi label={t('metric.deliveryRate')} value={fmtPercent(ratio(tot.sent, tot.sent + tot.failed))} />
            <Kpi label={t('metric.clicks')} value={fmtCompact(tot.clicks)} sub={t('metric.opensN', { n: fmtNumber(tot.opens) })} />
            <Kpi label={t('metric.conversions')} value={fmtCompact(tot.conversions)} sub={fmtPercent(ratio(tot.conversions, tot.target))} />
            <Kpi label={t('metric.spent')} value={fmtCompact(tot.spent)} sub={t('campaign.detail.ofBudget', { v: fmtCompact(c.budget.max ?? c.budget.expected) })} />
          </>
        )}
      </div>

      {/* 3.2.8.1 Hiệu quả kênh theo thời gian */}
      <ChartCard
        title={<TitleWithInfo title={t('campaign.detail.perfTitle')} info={t('campaign.detail.perfInfo')} />}
        loading={r.isLoading}
        height={320}
        actions={<Select className="w-40" value={channel} onValueChange={setChannel} allLabel={t('campaign.detail.allChannels')} options={(['SMS', 'EMAIL', 'WHATSAPP', 'USSD'] as const).map((ch) => ({ value: ch, label: t(`channel.${ch}`) }))} />}
        table={r.data && { columns: [t('chart.date'), ...series.map((s) => s.label)], rows: r.data.timeseries.map((p) => [p.date, ...series.map((s) => fmtNumber(p[s.key]))]) }}
      >
        {r.data && <MetricLines data={r.data.timeseries} xKey="date" series={series} xFormat={(d) => (bucket === 'MONTH' ? d.slice(5, 7) + '/' + d.slice(0, 4) : d.slice(8, 10) + '/' + d.slice(5, 7))} />}
      </ChartCard>

      <div className="grid gap-6 xl:grid-cols-2">
        {/* 3.2.8.2 Tỷ lệ gửi thành công theo kênh */}
        <Card>
          <CardHeader>
            <CardTitle>
              <TitleWithInfo title={t('metric.deliveryRate')} info={t('campaign.detail.deliveryInfo')} />
            </CardTitle>
          </CardHeader>
          <CardContent>
            {channels.length === 0 ? (
              <p className="py-10 text-center text-sm text-muted-foreground">{t('common.noData')}</p>
            ) : (
              <ul className="grid gap-4">
                {channels.map((ch) => {
                  const M = CHANNEL_META[ch.channel]
                  const rate = ratio(ch.sent, ch.sent + ch.failed)
                  return (
                    <li key={ch.channel} className="grid grid-cols-[104px_1fr_56px] items-center gap-3 text-[13px]">
                      <span className="flex items-center gap-2 text-foreground/80">
                        <M.icon className="size-4" style={{ color: M.color }} />
                        {t(`channel.${ch.channel}`)}
                      </span>
                      <Tooltip content={t('campaign.detail.deliveryTip', { sent: fmtNumber(ch.sent), failed: fmtNumber(ch.failed) })}>
                        <div className="h-6 rounded-md bg-muted/70">
                          <div className="h-full rounded-md" style={{ width: `${Math.max(rate > 0 ? 1.5 : 0, rate * 100)}%`, background: M.color }} />
                        </div>
                      </Tooltip>
                      <span className="text-right font-semibold tabular">{fmtPercent(rate)}</span>
                    </li>
                  )
                })}
              </ul>
            )}
          </CardContent>
        </Card>

        {/* 3.2.8.3 Tỷ lệ tương tác theo kênh */}
        <ChartCard
          title={<TitleWithInfo title={t('campaign.detail.interactionTitle')} info={t('campaign.detail.interactionInfo')} />}
          loading={r.isLoading}
          height={260}
          table={{ columns: [t('campaign.detail.metricCol'), ...chSeries.map((s) => s.label)], rows: interaction.map((row) => [row.metric, ...chSeries.map((s) => fmtPercent((row as Record<string, unknown>)[s.key] as number | null))]) }}
        >
          {channels.length ? <GroupedRateBars data={interaction} xKey="metric" series={chSeries} /> : <p className="py-10 text-center text-sm text-muted-foreground">{t('common.noData')}</p>}
        </ChartCard>
      </div>

      {/* 3.2.8.4–6 Donut tỷ lệ mở / click / đăng ký theo kênh */}
      {dCh && (
        <div className="grid gap-4">
          <div className="flex items-center justify-end gap-2 text-sm">
            <span className="text-muted-foreground">{t('campaign.detail.donutChannel')}</span>
            <Select className="w-40" value={dCh.channel} onValueChange={(v) => setDonutChannel(v as Channel)} options={channels.map((ch) => ({ value: ch.channel, label: t(`channel.${ch.channel}`) }))} />
          </div>
          <div className="grid gap-6 lg:grid-cols-3">
            <Card>
              <CardHeader>
                <CardTitle>
                  <TitleWithInfo title={t('campaign.detail.donutOpen', { channel: dName })} info={t('campaign.detail.donutOpenInfo')} />
                </CardTitle>
              </CardHeader>
              <CardContent>
                {dCh.channel === 'SMS' ? (
                  <p className="py-12 text-center text-sm text-muted-foreground">{t('campaign.detail.smsNoOpen')}</p>
                ) : (
                  <RateDonut rate={ratio(dCh.opened, dCh.delivered)} count={dCh.opened} centerValue={dCh.sent} centerLabel={t('campaign.detail.totalSent')} label={t('campaign.detail.rate.open')} />
                )}
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>
                  <TitleWithInfo title={t('campaign.detail.donutClick', { channel: dName })} info={t('campaign.detail.donutClickInfo')} />
                </CardTitle>
              </CardHeader>
              <CardContent>
                <RateDonut rate={ratio(dCh.clicked, dCh.delivered)} count={dCh.clicked} centerValue={dCh.clicked} centerLabel={t('campaign.detail.totalClicks')} label={t('campaign.detail.rate.clickShort')} />
              </CardContent>
            </Card>
            <Card>
              <CardHeader>
                <CardTitle>
                  <TitleWithInfo title={t('campaign.detail.donutSignup', { channel: dName })} info={t('campaign.detail.donutSignupInfo')} />
                </CardTitle>
              </CardHeader>
              <CardContent>
                {noGoal ? (
                  <p className="py-12 text-center text-sm text-muted-foreground">{t('campaign.detail.noGoal')}</p>
                ) : (
                  <RateDonut rate={ratio(dCh.converted, dCh.sent)} count={dCh.converted} centerValue={dCh.converted} centerLabel={t('campaign.detail.totalReg')} label={t('campaign.detail.rate.signup')} />
                )}
              </CardContent>
            </Card>
          </div>
        </div>
      )}

      {/* 3.2.8.7 Bảng hiệu quả theo nhóm hành động */}
      <Card>
        <CardHeader>
          <div>
            <CardTitle>
              <TitleWithInfo title={t('campaign.detail.byGroup')} info={t('campaign.detail.byGroupInfo')} />
            </CardTitle>
            <CardDescription>{t('campaign.detail.byGroupDesc')}</CardDescription>
          </div>
        </CardHeader>
        <CardContent className="overflow-x-auto px-0">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-y bg-muted/50 text-xs whitespace-nowrap text-muted-foreground uppercase">
                <th className="px-5 py-2 text-right font-medium">{t('common.no')}</th>
                <th className="px-3 py-2 text-left font-medium">{t('campaign.detail.group')}</th>
                <th className="px-3 py-2 text-left font-medium">{t('campaign.col.segment')}</th>
                <th className="px-3 py-2 text-right font-medium">{t('metric.deliveryRate')}</th>
                <th className="px-3 py-2 text-right font-medium">{t('metric.delivered')}</th>
                <th className="px-3 py-2 text-right font-medium">{t('metric.openRate')}</th>
                <th className="px-3 py-2 text-right font-medium">{t('metric.opens')}</th>
                <th className="px-3 py-2 text-right font-medium">{t('metric.conversionRate')}</th>
                <th className="px-3 py-2 text-right font-medium">{t('metric.conversions')}</th>
                <th className="px-5 py-2 text-right font-medium">Uplift</th>
              </tr>
            </thead>
            <tbody>
              {groups.map((g, i) => (
                <tr key={g.groupName + i} className="border-b whitespace-nowrap last:border-0">
                  <td className="px-5 py-3 text-right text-muted-foreground tabular">{i + 1}</td>
                  <td className="px-3 py-3">
                    <span className="flex items-center gap-2">
                      {g.groupName}
                      {g.isControl && g.groupName !== t('campaign.wizard.control') && <Badge>{t('campaign.wizard.control')}</Badge>}
                    </span>
                  </td>
                  <td className="max-w-48 truncate px-3 py-3 text-muted-foreground">{c.segmentName ?? '—'}</td>
                  <td className="px-3 py-3 text-right tabular">{g.isControl ? '—' : fmtPercent(g.sentRate)}</td>
                  <td className="px-3 py-3 text-right tabular">{g.isControl ? '—' : fmtNumber(g.delivered)}</td>
                  <td className="px-3 py-3 text-right tabular">{g.isControl ? '—' : fmtPercent(g.openRate)}</td>
                  <td className="px-3 py-3 text-right tabular">{g.isControl ? '—' : fmtNumber(g.opens)}</td>
                  <td className="px-3 py-3 text-right tabular">{fmtPercent(g.conversionRate)}</td>
                  <td className="px-3 py-3 text-right tabular">{fmtNumber(g.conversions)}</td>
                  <td className="px-5 py-3 text-right tabular">
                    {g.uplift == null ? (
                      '—'
                    ) : (
                      <span className={cn('font-semibold', g.uplift > 0 ? 'text-success' : g.uplift < 0 ? 'text-danger' : 'text-muted-foreground')}>
                        {g.uplift > 0 ? '+' : ''}
                        {g.uplift.toFixed(1)} pp
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <div>
            <CardTitle>{t('campaign.detail.funnel')}</CardTitle>
            <CardDescription>{t('campaign.detail.funnelDesc')}</CardDescription>
          </div>
        </CardHeader>
        <CardContent>
          {tot ? (
            <FunnelBars
              steps={[
                { label: t('metric.target'), value: tot.target },
                { label: t('metric.sent'), value: tot.sent },
                { label: t('metric.delivered'), value: tot.delivered },
                { label: t('metric.opens'), value: tot.opens },
                { label: t('metric.clicks'), value: tot.clicks },
                { label: t('metric.conversions'), value: tot.conversions },
              ]}
            />
          ) : (
            <Skeleton className="h-60" />
          )}
        </CardContent>
      </Card>
    </div>
  )
}

/** SRS 3.2.8.8 #11: diễn giải lịch chạy thành câu ("Mỗi 1 tuần vào T2, T5 lúc 09:00"). */
function scheduleRule(c: CampaignDetail, t: (k: string, o?: Record<string, unknown>) => string): string | null {
  const s = c.schedule
  if (c.type === 'EVENT') return t('campaign.detail.ruleEvent', { event: c.eventTemplate ? t(`campaign.event.${c.eventTemplate}`, { defaultValue: c.eventTemplate }) : '—' })
  if (s.type === 'NOW') return t('campaign.detail.ruleNow')
  if (s.type === 'ONCE') return t('campaign.detail.ruleOnce', { at: fmtDateTime(s.startAt) })
  const unit = t(`campaign.wizard.unit.${s.unit ?? 'WEEK'}`)
  const days =
    s.unit === 'WEEK' && s.weekdays?.length
      ? ' · ' + [...s.weekdays].sort((a, b) => ((a + 6) % 7) - ((b + 6) % 7)).map((d) => t(`weekday.${d}`)).join(', ')
      : s.unit === 'MONTH' && s.monthDays?.length
        ? ' · ' + t('campaign.detail.onDays', { days: s.monthDays.join(', ') })
        : ''
  const rule = t('campaign.detail.ruleRecurring', { every: s.every ?? 1, unit, days, time: s.sendTime ?? '09:00' })
  return s.maxOccurrences ? `${rule} · ${t('campaign.detail.ruleMax', { n: s.maxOccurrences })}` : rule
}

/** `customers`: số KH của nhóm — số thực tế khi đã chạy, ≈ ước tính (target × %) khi chưa chạy. */
function GroupBlock({ g, customers, estimated }: { g: ActionGroup; customers: number; estimated: boolean }) {
  const { t } = useTranslation()
  return (
    <div className="rounded-xl border">
      <div className="flex items-center justify-between gap-3 border-b bg-muted/40 px-4 py-2.5">
        <div className="flex items-center gap-2 text-sm font-semibold">
          {g.name}
          {g.isControl && <Badge>{t('campaign.wizard.control')}</Badge>}
        </div>
        <div className="text-xs text-muted-foreground">
          {g.allocationPercent}% · {t(estimated ? 'campaign.detail.approxCustomers' : 'campaign.detail.customersN', { n: fmtNumber(customers) })} · {t(`campaign.wizard.mode.${g.mode}`)}
        </div>
      </div>
      {g.actions.length === 0 ? (
        <p className="px-4 py-3 text-sm text-muted-foreground">{t('campaign.wizard.controlNote')}</p>
      ) : (
        <ol className="divide-y">
          {g.actions.map((a, i) => {
            const Icon = actionIcon(a.type)
            return (
              <li key={a.id} className="flex gap-3 px-4 py-3">
                <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-accent text-brand-600">
                  <Icon className="size-4" />
                </span>
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2 text-sm font-medium">
                    {i + 1}. {t(`actionType.${a.type}`)}
                    {a.senderId && <Badge variant="outline">{a.senderId}</Badge>}
                    {a.delayMinutes > 0 && <span className="text-xs font-normal text-muted-foreground">+{Math.round(a.delayMinutes / 60)}h</span>}
                  </div>
                  {a.subject && (
                    <p className="mt-1 text-[13px]">
                      <span className="text-muted-foreground">{t('campaign.wizard.subject')}:</span> {a.subject}
                    </p>
                  )}
                  {a.content && <p className="mt-1 rounded-lg bg-muted/60 px-3 py-2 text-[13px] whitespace-pre-wrap text-foreground/85">{a.content}</p>}
                  {a.templateId && <p className="mt-1 font-mono text-xs text-muted-foreground">template: {a.templateId}</p>}
                  {a.amount != null && <p className="mt-1 text-[13px] text-muted-foreground">{t('campaign.wizard.amount')}: {fmtNumber(a.amount)}</p>}
                  {a.packageCode && (
                    <p className="mt-1 text-[13px] text-muted-foreground">
                      {t('campaign.wizard.packageCode')}: <span className="font-mono text-foreground">{a.packageCode}</span>
                    </p>
                  )}
                </div>
              </li>
            )
          })}
        </ol>
      )}
    </div>
  )
}

function ConfigTab({ c }: { c: CampaignDetail }) {
  const { t } = useTranslation()
  const can = useCan()
  const canUpdate = can('CAMPAIGN_UPDATE')
  const started = ['RUNNING', 'PAUSED', 'COMPLETED'].includes(c.status) || c.history.some((h) => h.action === 'START')
  // Số KH thực tế theo nhóm (đã chạy) lấy từ báo cáo; chưa chạy → ước tính target × tỷ lệ phân bổ
  const report = useCampaignReport(c.id, { bucket: 'DAY' }, started && can('REPORT_VIEW'))
  const rule = scheduleRule(c, t)
  const rows: [React.ElementType, string, React.ReactNode][] = [
    [
      Users2,
      t('campaign.wizard.steps.audience'),
      c.segmentId ? (
        <span className="flex flex-wrap items-center gap-2">
          <Link className="text-link hover:underline" to={`/segments/${c.segmentId}`}>
            {c.segmentName}
          </Link>
          <Badge variant="outline">{t('campaign.detail.customersN', { n: fmtNumber(c.targetCount) })}</Badge>
        </span>
      ) : c.testIsdns.length ? (
        t('campaign.detail.testList', { n: c.testIsdns.length })
      ) : (
        '—'
      ),
    ],
    [
      CalendarClock,
      t('campaign.wizard.steps.schedule'),
      <span className="grid gap-0.5">
        {c.type !== 'EVENT' && (
          <span>
            {fmtDateTime(c.schedule.startAt)}
            {c.schedule.endAt ? ' → ' + fmtDateTime(c.schedule.endAt) : ''}
          </span>
        )}
        {rule && <span className="text-muted-foreground">{rule}</span>}
        {c.schedule.silentHours?.enabled && (
          <span className="text-muted-foreground">
            {t('campaign.wizard.silent')} {c.schedule.silentHours.from}–{c.schedule.silentHours.to}
          </span>
        )}
      </span>,
    ],
    [Goal, t('campaign.wizard.steps.goal'), `${t(`campaign.wizard.goal.${c.goal.mode}`)} · ${t('campaign.wizard.window', { n: c.goal.attributionWindowDays })}`],
    [Wallet, t('campaign.wizard.budget'), `${fmtKip(c.budget.expected)}${c.budget.max ? ` (${t('campaign.wizard.maxBudget')}: ${fmtKip(c.budget.max)})` : ''}`],
  ]
  return (
    <div className="grid gap-6 xl:grid-cols-[1fr_380px]">
      <Card>
        <CardHeader>
          <CardTitle>{t('campaign.wizard.steps.actions')}</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4">
          {c.actionGroups.map((g, i) => {
            const actual = report.data?.groups[i]
            return <GroupBlock key={g.id} g={g} customers={actual ? actual.target : Math.round((c.targetCount * g.allocationPercent) / 100)} estimated={!actual} />
          })}
        </CardContent>
      </Card>
      <Card className="h-fit">
        <CardHeader>
          <CardTitle>{t('campaign.detail.summary')}</CardTitle>
        </CardHeader>
        <CardContent className="grid gap-4">
          {rows.map(([Icon, label, val]) => (
            <div key={label} className="flex gap-3">
              <Icon className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
              <div className="min-w-0">
                <div className="text-xs text-muted-foreground">{label}</div>
                <div className="mt-0.5 text-sm">{val}</div>
              </div>
            </div>
          ))}
          {c.goal.description && <p className="rounded-lg bg-muted/60 px-3 py-2 text-[13px] text-foreground/80">{c.goal.description}</p>}
          {c.goal.mode === 'MANUAL_FILE' && canUpdate && ['RUNNING', 'PAUSED', 'COMPLETED'].includes(c.status) && <ConversionImport campaignId={c.id} />}
        </CardContent>
      </Card>
    </div>
  )
}

function HistoryTab({ c }: { c: CampaignDetail }) {
  const { t } = useTranslation()
  return (
    <Card>
      <CardContent>
        <ol className="relative ml-2 border-l pl-6">
          {[...c.history].reverse().map((h, i) => (
            <li key={i} className="relative pb-6 last:pb-0">
              <span className={cn('absolute top-1 -left-[31px] size-3 rounded-full ring-4 ring-card', h.action === 'REJECT' ? 'bg-danger' : h.action === 'APPROVE' ? 'bg-success' : 'bg-brand-500')} />
              <div className="text-sm font-medium">{t(`campaign.history.${h.action}`, { defaultValue: h.action })}</div>
              <div className="text-xs text-muted-foreground">
                {h.actor} · {fmtDateTime(h.at)}
              </div>
              {h.note && <p className="mt-1.5 rounded-lg bg-muted/60 px-3 py-2 text-[13px]">{h.note}</p>}
            </li>
          ))}
        </ol>
      </CardContent>
    </Card>
  )
}

export default function CampaignDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const cid = Number(id)
  const { data: c, isLoading, isError } = useCampaign(Number.isFinite(cid) ? cid : null)
  const { ops, run, setConfirm, dialogs, pending } = useCampaignOps(c ?? { id: cid, name: '', status: 'DRAFT' })

  if (isLoading) return <Skeleton className="h-96 w-full rounded-xl" />
  if (isError || !c) return <EmptyState icon={<Megaphone />} title={t('status.notFoundTitle')} />

  return (
    <>
      <PageHeader
        crumbs={[{ label: t('nav.campaigns'), to: '/campaigns' }, { label: c.code }]}
        icon={c.mode === 'TEST' ? <FlaskConical /> : <Rocket />}
        title={c.name}
        description={
          <span className="flex flex-wrap items-center gap-2">
            <CampaignStatusBadge status={c.status} />
            <Badge variant={c.mode === 'TEST' ? 'info' : 'brand'}>{t(`campaign.mode.${c.mode}`)}</Badge>
            <span className="text-muted-foreground">
              {fmtDate(c.startAt)} → {fmtDate(c.endAt)} · {t('campaign.by', { who: c.createdBy })}
            </span>
          </span>
        }
        actions={
          <>
            {ops.duplicate && (
              <Button variant="outline" onClick={() => run('duplicate')} disabled={pending}>
                <Copy />
                {t('campaign.ops.duplicate')}
              </Button>
            )}
            {ops.cancel && (
              <Button variant="outline" onClick={() => setConfirm('cancel')}>
                <Ban />
                {t('campaign.ops.cancel')}
              </Button>
            )}
            {ops.pause && (
              <Button variant="outline" onClick={() => run('pause')} loading={pending}>
                <Pause />
                {t('campaign.ops.pause')}
              </Button>
            )}
            {ops.resume && (
              <Button onClick={() => run('resume')} loading={pending}>
                <Play />
                {t('campaign.ops.resume')}
              </Button>
            )}
            {ops.edit && (
              <Button variant="outline" asChild>
                <Link to={`/campaigns/${c.id}/edit`}>
                  <Pencil />
                  {t('common.edit')}
                </Link>
              </Button>
            )}
            {ops.submit && (
              <Button onClick={() => run('submit')} loading={pending}>
                <Send />
                {t('campaign.ops.submit')}
              </Button>
            )}
          </>
        }
      />
      {c.status === 'REJECTED' && c.rejectReason && (
        <div className="mb-6 flex gap-3 rounded-xl border border-danger/25 bg-danger-soft px-4 py-3 text-sm">
          <AlertOctagon className="mt-0.5 size-4 shrink-0 text-danger" />
          <div>
            <div className="font-semibold text-danger">{t('campaign.detail.rejected')}</div>
            <div className="mt-0.5 text-foreground/85">{c.rejectReason}</div>
          </div>
        </div>
      )}
      {c.status === 'PENDING_APPROVAL' && (
        <div className="mb-6 flex items-center gap-3 rounded-xl border border-warning/25 bg-warning-soft px-4 py-3 text-sm">
          <CheckCircle2 className="size-4 shrink-0 text-warning" />
          {t('campaign.detail.pendingNote')}
        </div>
      )}
      <Tabs defaultValue="report">
        <TabsList variant="underline" className="mb-6">
          <TabsTrigger value="report">
            <Megaphone />
            {t('campaign.detail.tabReport')}
          </TabsTrigger>
          <TabsTrigger value="config">
            <Settings2 />
            {t('campaign.detail.tabConfig')}
          </TabsTrigger>
          <TabsTrigger value="history">
            <History />
            {t('campaign.detail.tabHistory')}
          </TabsTrigger>
        </TabsList>
        <TabsContent value="report">
          <ReportTab c={c} />
        </TabsContent>
        <TabsContent value="config">
          <ConfigTab c={c} />
        </TabsContent>
        <TabsContent value="history">
          <HistoryTab c={c} />
        </TabsContent>
      </Tabs>
      {dialogs}
    </>
  )
}

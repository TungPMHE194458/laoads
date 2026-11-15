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
  Megaphone,
  Pause,
  Pencil,
  Play,
  Rocket,
  Send,
  Settings2,
  Users2,
  Wallet,
} from 'lucide-react'
import type { ActionGroup, CampaignDetail } from '@/api/types'
import { useCampaign, useCampaignReport } from '@/features/campaign/api'
import { useCan } from '@/features/auth/permissions'
import { PageHeader } from '@/components/common/PageHeader'
import { ChartCard } from '@/components/common/ChartCard'
import { CampaignStatusBadge, CHANNEL_META } from '@/components/common/status'
import { FunnelBars, RateBar, TrendArea } from '@/components/charts/charts'
import { EmptyState } from '@/components/common/EmptyState'
import { Button } from '@/components/ui/button'
import { ConversionImport } from './ConversionImport'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton, Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/misc'
import { fmtCompact, fmtDate, fmtDateTime, fmtKip, fmtNumber, fmtPercent } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useCampaignOps } from './campaignActions'
import { actionIcon } from './wizard/actionMeta'

type Metric = 'sent' | 'opens' | 'clicks' | 'conversions'

function Kpi({ label, value, sub }: { label: string; value: React.ReactNode; sub?: React.ReactNode }) {
  return (
    <div className="rounded-xl border bg-card p-4 shadow-card">
      <div className="text-xs font-medium text-muted-foreground">{label}</div>
      <div className="mt-1.5 text-[22px] leading-tight font-semibold tabular">{value}</div>
      {sub && <div className="mt-1 text-xs text-muted-foreground">{sub}</div>}
    </div>
  )
}

function ReportTab({ c }: { c: CampaignDetail }) {
  const { t } = useTranslation()
  const [bucket, setBucket] = React.useState<'DAY' | 'WEEK' | 'MONTH'>('DAY')
  const [metric, setMetric] = React.useState<Metric>('conversions')
  const r = useCampaignReport(c.id, bucket)
  const started = ['RUNNING', 'PAUSED', 'COMPLETED'].includes(c.status)

  if (!started)
    return (
      <Card>
        <EmptyState icon={<CalendarClock />} title={t('campaign.detail.notStarted')} description={t('campaign.detail.notStartedDesc')} />
      </Card>
    )
  const tot = r.data?.totals
  return (
    <div className="grid gap-6">
      <div className="grid gap-4 sm:grid-cols-3 xl:grid-cols-6">
        {r.isLoading || !tot ? (
          Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-[92px] rounded-xl" />)
        ) : (
          <>
            <Kpi label={t('metric.target')} value={fmtCompact(tot.target)} />
            <Kpi label={t('metric.sent')} value={fmtCompact(tot.sent)} sub={t('metric.failedN', { n: fmtNumber(tot.failed) })} />
            <Kpi label={t('metric.deliveryRate')} value={fmtPercent(tot.sent ? tot.delivered / tot.sent : 0)} />
            <Kpi label={t('metric.openRate')} value={fmtPercent(tot.delivered ? tot.opens / tot.delivered : 0)} />
            <Kpi label={t('metric.conversions')} value={fmtCompact(tot.conversions)} sub={fmtPercent(tot.target ? tot.conversions / tot.target : 0)} />
            <Kpi label={t('metric.spent')} value={fmtCompact(tot.spent)} sub={t('campaign.detail.ofBudget', { v: fmtCompact(c.budget.max ?? c.budget.expected) })} />
          </>
        )}
      </div>

      <div className="grid gap-6 xl:grid-cols-5">
        <ChartCard
          className="xl:col-span-3"
          title={t('campaign.detail.trend')}
          description={t(`metric.${metric}`)}
          loading={r.isLoading}
          actions={
            <>
              <div className="flex rounded-lg border p-0.5 text-xs">
                {(['sent', 'opens', 'clicks', 'conversions'] as Metric[]).map((m) => (
                  <button key={m} aria-pressed={metric === m} onClick={() => setMetric(m)} className={cn('cursor-pointer rounded-md px-2 py-1 transition', metric === m ? 'bg-accent font-medium text-accent-foreground' : 'text-muted-foreground hover:text-foreground')}>
                    {t(`metric.${m}`)}
                  </button>
                ))}
              </div>
              <div className="flex rounded-lg border p-0.5 text-xs">
                {(['DAY', 'WEEK', 'MONTH'] as const).map((b) => (
                  <button key={b} aria-pressed={bucket === b} onClick={() => setBucket(b)} className={cn('cursor-pointer rounded-md px-2 py-1 transition', bucket === b ? 'bg-muted font-medium text-foreground' : 'text-muted-foreground hover:text-foreground')}>
                    {t(`campaign.detail.bucket.${b}`)}
                  </button>
                ))}
              </div>
            </>
          }
          table={r.data && { columns: [t('chart.date'), t(`metric.${metric}`)], rows: r.data.timeseries.map((p) => [p.date, fmtNumber(p[metric])]) }}
        >
          {r.data && <TrendArea data={r.data.timeseries} xKey="date" yKey={metric} valueLabel={t(`metric.${metric}`)} xFormat={(d) => d.slice(8, 10) + '/' + d.slice(5, 7)} />}
        </ChartCard>
        <Card className="xl:col-span-2">
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

      <div className="grid gap-6 xl:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>{t('campaign.detail.byChannel')}</CardTitle>
          </CardHeader>
          <CardContent className="overflow-x-auto px-0">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-y bg-muted/50 text-xs text-muted-foreground uppercase">
                  <th className="px-5 py-2 text-left font-medium">{t('campaign.col.channels')}</th>
                  <th className="px-3 py-2 text-right font-medium">{t('metric.sent')}</th>
                  <th className="px-3 py-2 text-left font-medium">{t('metric.deliveryRate')}</th>
                  <th className="px-5 py-2 text-left font-medium">{t('metric.openRate')}</th>
                </tr>
              </thead>
              <tbody>
                {r.data?.channels.map((ch) => {
                  const M = CHANNEL_META[ch.channel]
                  return (
                    <tr key={ch.channel} className="border-b last:border-0">
                      <td className="px-5 py-3">
                        <span className="flex items-center gap-2">
                          <M.icon className="size-4" style={{ color: M.color }} />
                          {t(`channel.${ch.channel}`)}
                        </span>
                      </td>
                      <td className="px-3 py-3 text-right tabular">{fmtNumber(ch.sent)}</td>
                      <td className="px-3 py-3">
                        <RateBar value={ch.sent ? ch.delivered / ch.sent : 0} color={M.color} />
                      </td>
                      <td className="px-5 py-3">{ch.channel === 'SMS' ? <span className="text-xs text-muted-foreground">{t('campaign.detail.smsNoOpen')}</span> : <RateBar value={ch.delivered ? ch.opened / ch.delivered : 0} color={M.color} />}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <div>
              <CardTitle>{t('campaign.detail.byGroup')}</CardTitle>
              <CardDescription>{t('campaign.detail.byGroupDesc')}</CardDescription>
            </div>
          </CardHeader>
          <CardContent className="overflow-x-auto px-0">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-y bg-muted/50 text-xs text-muted-foreground uppercase">
                  <th className="px-5 py-2 text-left font-medium">{t('campaign.detail.group')}</th>
                  <th className="px-3 py-2 text-right font-medium">{t('metric.target')}</th>
                  <th className="px-3 py-2 text-right font-medium">{t('metric.conversionRate')}</th>
                  <th className="px-5 py-2 text-right font-medium">Uplift</th>
                </tr>
              </thead>
              <tbody>
                {r.data?.groups.map((g) => (
                  <tr key={g.groupName} className="border-b last:border-0">
                    <td className="px-5 py-3">
                      <span className="flex items-center gap-2">
                        {g.groupName}
                        {g.isControl && g.groupName !== t('campaign.wizard.control') && <Badge>{t('campaign.wizard.control')}</Badge>}
                      </span>
                    </td>
                    <td className="px-3 py-3 text-right tabular">{fmtNumber(g.target)}</td>
                    <td className="px-3 py-3 text-right tabular">{fmtPercent(g.conversionRate)}</td>
                    <td className="px-5 py-3 text-right whitespace-nowrap tabular">{g.uplift == null ? '—' : <span className="font-semibold text-success">+{g.uplift.toFixed(1)} pp</span>}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

function GroupBlock({ g }: { g: ActionGroup }) {
  const { t } = useTranslation()
  return (
    <div className="rounded-xl border">
      <div className="flex items-center justify-between gap-3 border-b bg-muted/40 px-4 py-2.5">
        <div className="flex items-center gap-2 text-sm font-semibold">
          {g.name}
          {g.isControl && <Badge>{t('campaign.wizard.control')}</Badge>}
        </div>
        <div className="text-xs text-muted-foreground">
          {g.allocationPercent}% · {t(`campaign.wizard.mode.${g.mode}`)}
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
                  {a.content && <p className="mt-1 rounded-lg bg-muted/60 px-3 py-2 text-[13px] whitespace-pre-wrap text-foreground/85">{a.content}</p>}
                  {a.amount != null && <p className="mt-1 text-[13px] text-muted-foreground">{t('campaign.wizard.amount')}: {fmtNumber(a.amount)}</p>}
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
  const canUpdate = useCan()('CAMPAIGN_UPDATE')
  const rows: [React.ElementType, string, React.ReactNode][] = [
    [Users2, t('campaign.wizard.steps.audience'), c.segmentId ? <Link className="text-link hover:underline" to={`/segments/${c.segmentId}`}>{c.segmentName}</Link> : '—'],
    [CalendarClock, t('campaign.wizard.steps.schedule'), `${t(`campaign.wizard.schedule.${c.schedule.type}`)} · ${fmtDateTime(c.schedule.startAt)}${c.schedule.endAt ? ' → ' + fmtDateTime(c.schedule.endAt) : ''}${c.schedule.silentHours?.enabled ? ` · ${t('campaign.wizard.silent')} ${c.schedule.silentHours.from}–${c.schedule.silentHours.to}` : ''}`],
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
          {c.actionGroups.map((g) => (
            <GroupBlock key={g.id} g={g} />
          ))}
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

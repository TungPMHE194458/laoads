import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { ArrowRight, BadgeCheck, CalendarDays, Megaphone, Rocket, UsersRound } from 'lucide-react'
import { useMe } from '@/features/auth/api'
import { useCan, useCurrentWorkspace } from '@/features/auth/permissions'
import { useApprovals, useCampaigns, useDashboardChannels, useDashboardSummary, useDashboardTrend } from '@/features/campaign/api'
import { StatCard } from '@/components/common/StatCard'
import { ChartCard } from '@/components/common/ChartCard'
import { ChannelChips, CHANNEL_META } from '@/components/common/status'
import { ChannelShareBar, RateBar, TrendArea } from '@/components/charts/charts'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Progress, Skeleton } from '@/components/ui/misc'
import { EmptyState } from '@/components/common/EmptyState'
import { fmtCompact, fmtDate, fmtNumber, fmtPercent, fmtRelative } from '@/lib/format'

function greetingKey() {
  const h = new Date().getHours()
  return h < 11 ? 'dashboard.morning' : h < 18 ? 'dashboard.afternoon' : 'dashboard.evening'
}

const shortDate = (d: string) => d.slice(8, 10) + '/' + d.slice(5, 7)

export default function DashboardPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const can = useCan()
  const { data: me } = useMe()
  const ws = useCurrentWorkspace()
  const [days, setDays] = React.useState(30)
  const [now] = React.useState(() => Date.now())
  const summary = useDashboardSummary(7)
  const trend = useDashboardTrend(days)
  const channels = useDashboardChannels(days)
  const running = useCampaigns({ page: 0, size: 5, status: 'RUNNING' })
  const approvals = useApprovals({ page: 0, size: 4 }, can('CAMPAIGN_APPROVE'))
  const s = summary.data

  // Dùng nguyên họ tên: tên người Lào đặt tên gọi TRƯỚC họ, tên Việt đặt SAU — cắt chữ sẽ sai một bên
  const displayName = me?.user.fullName ?? me?.user.username ?? ''

  return (
    <div className="grid gap-6">
      {/* Hero */}
      <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="flex items-center gap-2 text-[13px] text-muted-foreground">
            <CalendarDays className="size-4" />
            {fmtDate(new Date())} · {ws?.name}
          </p>
          <h1 className="mt-1.5 text-[26px] font-semibold tracking-tight">
            {t(greetingKey())}, <span className="text-brand-600 dark:text-brand-400">{displayName}</span> 👋
          </h1>
          <p className="mt-1 text-sm text-muted-foreground">{t('dashboard.subtitle')}</p>
        </div>
        <div className="flex items-center gap-2">
          <div className="flex rounded-lg border bg-card p-0.5 text-[13px] shadow-xs" role="group" aria-label={t('dashboard.period')}>
            {[7, 30, 90].map((d) => (
              <button
                key={d}
                aria-pressed={days === d}
                onClick={() => setDays(d)}
                className={`cursor-pointer rounded-md px-3 py-1.5 font-medium whitespace-nowrap transition ${days === d ? 'bg-accent text-accent-foreground' : 'text-muted-foreground hover:text-foreground'}`}
              >
                {t('dashboard.lastDays', { n: d })}
              </button>
            ))}
          </div>
        </div>
      </div>

      {/* KPI */}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label={t('dashboard.running')} metric={s?.runningCampaigns} loading={summary.isLoading} icon={<Rocket />} />
        <StatCard label={t('dashboard.pending')} metric={s?.pendingApprovals} loading={summary.isLoading} icon={<BadgeCheck />} />
        <StatCard label={t('dashboard.total')} metric={s?.totalCampaigns} loading={summary.isLoading} icon={<Megaphone />} />
        <StatCard label={t('dashboard.reached')} metric={s?.reachedCustomers} loading={summary.isLoading} icon={<UsersRound />} format={fmtCompact} />
      </div>

      {/* Charts */}
      <div className="grid gap-6 xl:grid-cols-3">
        <ChartCard
          className="xl:col-span-2"
          title={t('dashboard.trendTitle')}
          description={t('dashboard.trendDesc', { n: days })}
          loading={trend.isLoading}
          table={trend.data && { columns: [t('chart.date'), t('metric.sent'), t('metric.opens'), t('metric.clicks'), t('metric.conversions')], rows: trend.data.map((p) => [p.date, fmtNumber(p.sent), fmtNumber(p.opens), fmtNumber(p.clicks), fmtNumber(p.conversions)]) }}
        >
          {trend.data && <TrendArea data={trend.data} xKey="date" yKey="conversions" valueLabel={t('metric.conversions')} xFormat={shortDate} />}
        </ChartCard>

        <Card>
          <CardHeader>
            <div>
              <CardTitle>{t('dashboard.channelTitle')}</CardTitle>
              <CardDescription>{t('dashboard.channelDesc')}</CardDescription>
            </div>
          </CardHeader>
          <CardContent className="pt-4">
            {channels.isLoading || !channels.data ? (
              <Skeleton className="h-56 w-full" />
            ) : (
              <>
                <ChannelShareBar items={channels.data.map((c) => ({ channel: c.channel, value: c.sent }))} />
                <div className="mt-5 border-t pt-4">
                  <div className="mb-2 grid grid-cols-[1fr_auto] text-xs text-muted-foreground">
                    <span>{t('dashboard.deliveryRate')}</span>
                  </div>
                  <ul className="grid gap-2.5">
                    {channels.data.map((c) => {
                      const M = CHANNEL_META[c.channel]
                      return (
                        <li key={c.channel} className="flex items-center justify-between gap-3 text-[13px]">
                          <span className="flex items-center gap-2">
                            <M.icon className="size-4" style={{ color: M.color }} />
                            {t(`channel.${c.channel}`)}
                          </span>
                          <RateBar value={c.sent ? c.delivered / c.sent : 0} color={M.color} />
                        </li>
                      )
                    })}
                  </ul>
                </div>
              </>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Lists */}
      <div className="grid gap-6 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader>
            <div>
              <CardTitle>{t('dashboard.runningList')}</CardTitle>
              <CardDescription>{t('dashboard.runningListDesc')}</CardDescription>
            </div>
            <Button asChild variant="ghost" size="sm">
              <Link to="/campaigns?status=RUNNING">
                {t('common.viewAll')}
                <ArrowRight />
              </Link>
            </Button>
          </CardHeader>
          <CardContent className="px-0 pb-2">
            {running.isLoading ? (
              <div className="grid gap-3 px-5">
                {[0, 1, 2].map((i) => (
                  <Skeleton key={i} className="h-12 w-full" />
                ))}
              </div>
            ) : !running.data?.items.length ? (
              <EmptyState icon={<Megaphone />} title={t('dashboard.noRunning')} className="py-10" />
            ) : (
              <ul>
                {running.data.items.map((c) => {
                  const total = c.endAt && c.startAt ? Date.parse(c.endAt) - Date.parse(c.startAt) : 1
                  const elapsed = c.startAt ? now - Date.parse(c.startAt) : 0
                  const pct = Math.max(2, Math.min(100, (elapsed / total) * 100))
                  return (
                    <li key={c.id}>
                      <button onClick={() => navigate(`/campaigns/${c.id}`)} className="grid w-full cursor-pointer grid-cols-[1fr_auto] items-center gap-4 border-t px-5 py-3.5 text-left transition hover:bg-muted/40 sm:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)_110px]">
                        <span className="min-w-0">
                          <span className="block truncate text-sm font-medium">{c.name}</span>
                          <span className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
                            <span className="font-mono">{c.code}</span>
                            <ChannelChips channels={c.channels} />
                          </span>
                        </span>
                        <span className="hidden sm:block">
                          <span className="mb-1.5 flex justify-between text-xs text-muted-foreground">
                            <span>{t('dashboard.progress')}</span>
                            <span className="tabular">{Math.round(pct)}%</span>
                          </span>
                          <Progress value={pct} />
                        </span>
                        <span className="text-right">
                          <span className="block text-sm font-semibold tabular">{fmtPercent(c.conversionRate)}</span>
                          <span className="text-xs text-muted-foreground">{t('metric.conversionRate')}</span>
                        </span>
                      </button>
                    </li>
                  )
                })}
              </ul>
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <div>
              <CardTitle>{t('dashboard.quickApprove')}</CardTitle>
              <CardDescription>{t('dashboard.quickApproveDesc')}</CardDescription>
            </div>
          </CardHeader>
          <CardContent className="px-0 pb-2">
            {!can('CAMPAIGN_APPROVE') ? (
              <p className="px-5 pb-4 text-sm text-muted-foreground">{t('dashboard.noApprovePerm')}</p>
            ) : !approvals.data?.items.length ? (
              <EmptyState icon={<BadgeCheck />} title={t('dashboard.allClear')} className="py-10" />
            ) : (
              <ul>
                {approvals.data.items.map((c) => (
                  <li key={c.id} className="flex items-center gap-3 border-t px-5 py-3">
                    <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-warning-soft text-warning">
                      <BadgeCheck className="size-4" />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium">{c.name}</span>
                      <span className="block text-xs text-muted-foreground">
                        {c.createdBy} · {fmtRelative(c.createdAt)}
                      </span>
                    </span>
                    <Button asChild size="sm" variant="soft">
                      <Link to={`/approvals?focus=${c.id}`}>{t('approval.review')}</Link>
                    </Button>
                  </li>
                ))}
              </ul>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

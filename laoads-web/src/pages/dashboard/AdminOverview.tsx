import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { AlertOctagon, ArrowRight, Building2, Download, Megaphone, Rocket, RotateCw, ScrollText, Trophy, Users } from 'lucide-react'
import { toast } from 'sonner'
import type { AuditLog } from '@/api/types'
import { downloadTopWorkspaces, useAdminDashboard, useAuditLogs } from '@/features/admin/api'
import { StatCard } from '@/components/common/StatCard'
import { DataTable } from '@/components/common/DataTable'
import { EmptyState } from '@/components/common/EmptyState'
import { useAuditColumns } from '@/pages/audit/auditColumns'
import { AuditDetailDialog } from '@/pages/audit/AuditDetailDialog'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/misc'
import { errorMessage } from '@/api/client'
import { fmtNumber } from '@/lib/format'

/** Nhật ký hệ thống trên Dashboard (SRS 3.1.8–3.1.9): 5 dòng / trang, đổi số dòng, xem chi tiết, "Xem tất cả". */
function RecentAuditLogs() {
  const { t } = useTranslation()
  const [page, setPage] = React.useState(0)
  const [size, setSize] = React.useState(5)
  const [viewing, setViewing] = React.useState<AuditLog | null>(null)
  const list = useAuditLogs({ page, size })
  const columns = useAuditColumns(setViewing, true)
  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-4">
        <div>
          <CardTitle>{t('adminDash.auditTitle')}</CardTitle>
          <CardDescription>{t('adminDash.auditDesc')}</CardDescription>
        </div>
        <Button variant="ghost" size="sm" asChild>
          <Link to="/audit-logs">
            {t('common.viewAll')}
            <ArrowRight />
          </Link>
        </Button>
      </CardHeader>
      <CardContent className="px-0 pb-0">
        <DataTable
          dense
          className="rounded-none border-x-0 border-b-0 shadow-none"
          columns={columns}
          rows={list.data?.items}
          rowKey={(a) => a.id}
          loading={list.isLoading}
          error={list.isError ? errorMessage(list.error) : undefined}
          meta={list.data?.meta}
          pageSizes={[5, 10, 20]}
          onPageChange={setPage}
          onPageSizeChange={(n) => {
            setSize(n)
            setPage(0)
          }}
          onRowClick={setViewing}
          empty={<EmptyState icon={<ScrollText />} title={t('common.noData')} />}
        />
      </CardContent>
      <AuditDetailDialog log={viewing} onOpenChange={(o) => !o && setViewing(null)} />
    </Card>
  )
}

/** Khối Dashboard dành cho Super Admin (SRS 3.1.1–3.1.4, 3.1.6–3.1.9): số liệu toàn hệ thống. */
export function AdminOverview() {
  const { t } = useTranslation()
  const q = useAdminDashboard(7)
  const d = q.data
  const [exporting, setExporting] = React.useState(false)
  const exportTop = async () => {
    setExporting(true)
    try {
      await downloadTopWorkspaces()
    } catch (e) {
      toast.error(errorMessage(e))
    } finally {
      setExporting(false)
    }
  }
  return (
    <section className="grid gap-4" aria-labelledby="admin-overview-title">
      <div>
        <h2 id="admin-overview-title" className="text-base font-semibold">{t('adminDash.title')}</h2>
        <p className="text-sm text-muted-foreground">{t('adminDash.subtitle')}</p>
      </div>
      {q.isError && (
        <div role="alert" className="flex flex-wrap items-center gap-3 rounded-xl border border-danger/25 bg-danger-soft px-4 py-3 text-sm text-danger">
          <AlertOctagon className="size-4 shrink-0" />
          <span className="flex-1">{t('adminDash.loadError', { message: errorMessage(q.error) })}</span>
          <Button variant="outline" size="sm" onClick={() => q.refetch()}>
            <RotateCw />
            {t('common.retry')}
          </Button>
        </div>
      )}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label={t('adminDash.activeWorkspaces')} metric={d?.activeWorkspaces} loading={q.isLoading} icon={<Building2 />} />
        <StatCard label={t('adminDash.totalUsers')} metric={d?.totalUsers} loading={q.isLoading} icon={<Users />} />
        <StatCard label={t('adminDash.runningCampaigns')} metric={d?.runningCampaigns} loading={q.isLoading} icon={<Rocket />} />
        <StatCard label={t('adminDash.totalCampaigns')} metric={d?.totalCampaigns} loading={q.isLoading} icon={<Megaphone />} />
      </div>
      <Card>
        <CardHeader className="flex flex-row items-start justify-between gap-4">
          <div>
            <CardTitle>{t('adminDash.topTitle')}</CardTitle>
            <CardDescription>{t('adminDash.topDesc')}</CardDescription>
          </div>
          <Button variant="outline" size="sm" onClick={exportTop} loading={exporting} disabled={!d?.topWorkspaces.length}>
            <Download />
            {t('adminDash.export')}
          </Button>
        </CardHeader>
        <CardContent className="px-0 pb-2">
          {q.isLoading ? (
            <div className="grid gap-2 px-5 pb-3">
              {[0, 1, 2].map((i) => (
                <Skeleton key={i} className="h-8" />
              ))}
            </div>
          ) : !d?.topWorkspaces.length ? (
            <EmptyState icon={<Trophy />} title={t('common.noData')} description={t('adminDash.topEmpty')} />
          ) : (
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs text-muted-foreground">
                  <th className="px-5 py-2 font-medium">#</th>
                  <th className="px-3 py-2 font-medium">{t('adminDash.workspace')}</th>
                  <th className="px-3 py-2 text-right font-medium">{t('adminDash.campaigns')}</th>
                  <th className="px-5 py-2 text-right font-medium">{t('adminDash.running')}</th>
                </tr>
              </thead>
              <tbody>
                {(d?.topWorkspaces ?? []).map((w, i) => (
                  <tr key={w.workspaceId} className="border-t">
                    <td className="px-5 py-2.5 text-muted-foreground tabular">{i + 1}</td>
                    <td className="px-3 py-2.5">
                      <span className="font-medium">{w.code}</span>
                      <span className="ml-2 text-muted-foreground">{w.name}</span>
                    </td>
                    <td className="px-3 py-2.5 text-right tabular">{fmtNumber(w.campaignCount)}</td>
                    <td className="px-5 py-2.5 text-right tabular">{fmtNumber(w.runningCount)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </CardContent>
      </Card>
      <RecentAuditLogs />
    </section>
  )
}

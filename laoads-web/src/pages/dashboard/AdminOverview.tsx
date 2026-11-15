import { useTranslation } from 'react-i18next'
import { Building2, Download, Megaphone, Rocket, Users } from 'lucide-react'
import { toast } from 'sonner'
import { downloadTopWorkspaces, useAdminDashboard } from '@/features/admin/api'
import { StatCard } from '@/components/common/StatCard'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/misc'
import { errorMessage } from '@/api/client'
import { fmtNumber } from '@/lib/format'

/** Khối Dashboard dành cho Super Admin (SRS 3.1.1, 3.1.4, 3.1.6, 3.1.7): số liệu toàn hệ thống. */
export function AdminOverview() {
  const { t } = useTranslation()
  const q = useAdminDashboard(7)
  const d = q.data
  const exportTop = async () => {
    try {
      await downloadTopWorkspaces()
    } catch (e) {
      toast.error(errorMessage(e))
    }
  }
  return (
    <section className="grid gap-4" aria-labelledby="admin-overview-title">
      <div>
        <h2 id="admin-overview-title" className="text-base font-semibold">{t('adminDash.title')}</h2>
        <p className="text-sm text-muted-foreground">{t('adminDash.subtitle')}</p>
      </div>
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
          <Button variant="outline" size="sm" onClick={exportTop}>
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
    </section>
  )
}

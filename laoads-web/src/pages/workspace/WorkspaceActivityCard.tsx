import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { ArrowRight, Activity } from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { AuditLog } from '@/api/types'
import { useAuditLogs } from '@/features/admin/api'
import { DataTable } from '@/components/common/DataTable'
import { EmptyState } from '@/components/common/EmptyState'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { AuditDetailDialog } from '@/pages/audit/AuditDetailDialog'
import { useAuditColumns } from '@/pages/audit/auditColumns'

/**
 * Tab "Hoạt động" của doanh nghiệp trên laoads.com → khối hoạt động gần đây của workspace (nhật ký đã lọc theo
 * workspace: user thường luôn bị BE giới hạn theo X-Workspace-Id; Super Admin truyền workspaceId để lọc).
 */
export function WorkspaceActivityCard({ wsId }: { wsId: number }) {
  const { t } = useTranslation()
  const [page, setPage] = React.useState(0)
  const [size, setSize] = React.useState(5)
  const [viewing, setViewing] = React.useState<AuditLog | null>(null)
  const list = useAuditLogs({ page, size, workspaceId: wsId })
  const columns = useAuditColumns(setViewing, true)
  return (
    <Card>
      <CardHeader className="flex flex-row items-start justify-between gap-4">
        <div>
          <CardTitle className="flex items-center gap-2">
            <Activity className="size-4 text-brand-600" />
            {t('workspace.activity')}
          </CardTitle>
          <CardDescription>{t('workspace.activityDesc')}</CardDescription>
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
          empty={<EmptyState icon={<Activity />} title={t('workspace.noActivity')} />}
        />
      </CardContent>
      <AuditDetailDialog log={viewing} onOpenChange={(o) => !o && setViewing(null)} />
    </Card>
  )
}

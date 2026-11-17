import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { requestPage } from '@/api/client'
import type { Member, Workspace } from '@/api/types'
import { ActiveBadge } from '@/components/common/status'
import { Badge } from '@/components/ui/badge'
import { Skeleton } from '@/components/ui/misc'
import { Dialog, DialogDescription, DialogTitle, SheetContent } from '@/components/ui/dialog'
import { fmtDateTime } from '@/lib/format'

/**
 * Super Admin xem chi tiết một workspace (SRS 3.3.2) — kể cả workspace đã khoá, vốn không chọn được ở bộ chuyển
 * workspace. Thông tin chung + liên hệ + thành viên (chỉ đọc). Gửi X-Workspace-Id = workspace đang xem để khớp
 * kiểm tra đường dẫn của BE.
 */
export function WorkspaceDetailSheet({ workspace, onClose }: { workspace: Workspace | null; onClose: () => void }) {
  const { t } = useTranslation()
  const id = workspace?.id ?? null
  const members = useQuery({
    queryKey: ['admin', 'workspace-members', id],
    queryFn: () => requestPage<Member>({ url: `/workspaces/${id}/members`, params: { page: 0, size: 100 }, headers: { 'X-Workspace-Id': String(id) } }),
    enabled: id != null,
  })
  const na = <span className="text-muted-foreground">{t('workspace.notUpdated')}</span>
  return (
    <Dialog open={!!workspace} onOpenChange={(o) => !o && onClose()}>
      <SheetContent className="sm:max-w-xl">
        {workspace && (
          <div className="grid gap-6 overflow-y-auto p-6">
            <div className="flex items-start gap-4">
              <span className="grid size-12 shrink-0 place-items-center rounded-xl bg-brand-gradient text-sm font-bold text-white">{workspace.code.slice(0, 3)}</span>
              <div className="min-w-0 flex-1">
                <DialogTitle className="truncate text-lg font-semibold">{workspace.name}</DialogTitle>
                <DialogDescription className="flex items-center gap-2 text-sm text-muted-foreground">
                  <span className="font-mono">{workspace.code}</span>
                  <ActiveBadge status={workspace.status} />
                </DialogDescription>
              </div>
            </div>
            <section>
              <h3 className="mb-2 text-sm font-semibold">{t('workspace.general')}</h3>
              <dl className="grid grid-cols-[9rem_1fr] gap-x-4 gap-y-2.5 text-sm">
                <dt className="text-muted-foreground">{t('segment.description')}</dt>
                <dd>{workspace.description || na}</dd>
                <dt className="text-muted-foreground">{t('common.createdAt')}</dt>
                <dd className="tabular">{fmtDateTime(workspace.createdAt)}</dd>
                <dt className="text-muted-foreground">{t('workspace.updatedAt')}</dt>
                <dd className="tabular">{workspace.updatedAt ? fmtDateTime(workspace.updatedAt) : na}</dd>
              </dl>
            </section>
            <section>
              <h3 className="mb-2 text-sm font-semibold">{t('workspace.contact')}</h3>
              <dl className="grid grid-cols-[9rem_1fr] gap-x-4 gap-y-2.5 text-sm">
                <dt className="text-muted-foreground">{t('workspace.contactName')}</dt>
                <dd>{workspace.contactName || na}</dd>
                <dt className="text-muted-foreground">{t('members.email')}</dt>
                <dd className="break-all">{workspace.contactEmail || na}</dd>
                <dt className="text-muted-foreground">{t('members.phone')}</dt>
                <dd className="tabular">{workspace.contactPhone || na}</dd>
              </dl>
            </section>
            <section>
              <h3 className="mb-2 text-sm font-semibold">
                {t('workspace.members')} <span className="font-normal text-muted-foreground tabular">({members.data?.meta.totalItems ?? workspace.memberCount})</span>
              </h3>
              {members.isLoading ? (
                <Skeleton className="h-32" />
              ) : members.isError ? (
                <p className="text-sm text-danger">{t('workspace.membersError')}</p>
              ) : (
                <ul className="divide-y rounded-lg border text-sm">
                  {(members.data?.items ?? []).map((m) => (
                    <li key={m.id} className="flex items-center gap-3 px-3 py-2">
                      <span className="min-w-0 flex-1 truncate">
                        {m.fullName ?? m.username}
                        <span className="ml-2 text-xs text-muted-foreground">@{m.username}</span>
                      </span>
                      <Badge variant={m.role === 'OWNER' ? 'brand' : m.role === 'ADMIN' ? 'info' : 'neutral'}>{t(`role.${m.role}`)}</Badge>
                      <ActiveBadge status={m.status} />
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        )}
      </SheetContent>
    </Dialog>
  )
}

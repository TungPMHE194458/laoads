import { useTranslation } from 'react-i18next'
import { Pencil, Trash2 } from 'lucide-react'
import type { Member } from '@/api/types'
import { ActiveBadge } from '@/components/common/status'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Dialog, DialogDescription, DialogTitle, SheetContent } from '@/components/ui/dialog'
import { fmtDateTime } from '@/lib/format'
import { initials } from '@/lib/utils'

/**
 * Chi tiết một thành viên (SRS 3.4.5) — drawer mở khi bấm vào dòng trong danh sách thành viên.
 * Nút Chỉnh sửa / Gỡ chỉ hiện khi được phép (không phải chính mình, không phải Chủ sở hữu, có quyền quản lý).
 */
export function MemberDetailSheet({ member, onClose, onEdit, onRemove }: { member: Member | null; onClose: () => void; onEdit?: () => void; onRemove?: () => void }) {
  const { t } = useTranslation()
  return (
    <Dialog open={!!member} onOpenChange={(o) => !o && onClose()}>
      <SheetContent>
        {member && (
          <div className="grid gap-6 p-6">
            <div className="flex items-center gap-4">
              <span className="grid size-14 shrink-0 place-items-center rounded-full bg-brand-gradient text-lg font-semibold text-white">
                {initials(member.fullName ?? member.username)}
              </span>
              <div className="min-w-0">
                {/* Giữ nguyên họ tên (tên Lào đặt tên gọi trước, tên Việt đặt sau) */}
                <DialogTitle className="truncate text-lg font-semibold">{member.fullName ?? member.username}</DialogTitle>
                <DialogDescription className="text-sm text-muted-foreground">@{member.username}</DialogDescription>
              </div>
            </div>
            <dl className="grid grid-cols-[8rem_1fr] gap-x-4 gap-y-3 text-sm">
              <dt className="text-muted-foreground">{t('members.role')}</dt>
              <dd>
                <Badge variant={member.role === 'OWNER' ? 'brand' : 'neutral'}>{t(`role.${member.role}`)}</Badge>
              </dd>
              <dt className="text-muted-foreground">{t('common.status')}</dt>
              <dd>
                <ActiveBadge status={member.status} />
              </dd>
              <dt className="text-muted-foreground">{t('members.email')}</dt>
              <dd className="break-all">{member.email ?? '—'}</dd>
              <dt className="text-muted-foreground">{t('members.phone')}</dt>
              <dd className="tabular">{member.phone ?? '—'}</dd>
              <dt className="text-muted-foreground">{t('members.joined')}</dt>
              <dd className="tabular">{fmtDateTime(member.joinedAt)}</dd>
            </dl>
            {(onEdit || onRemove) && (
              <div className="flex flex-wrap gap-2 border-t pt-4">
                {onEdit && (
                  <Button variant="outline" onClick={onEdit}>
                    <Pencil />
                    {t('members.editMember')}
                  </Button>
                )}
                {onRemove && (
                  <Button variant="outline" className="text-danger hover:text-danger" onClick={onRemove}>
                    <Trash2 />
                    {t('members.remove')}
                  </Button>
                )}
              </div>
            )}
          </div>
        )}
      </SheetContent>
    </Dialog>
  )
}

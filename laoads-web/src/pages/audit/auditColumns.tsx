import { useTranslation } from 'react-i18next'
import { Eye } from 'lucide-react'
import type { AuditLog } from '@/api/types'
import type { Column } from '@/components/common/DataTable'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { fmtDateTime } from '@/lib/format'
import { AUDIT_TONE } from './auditMeta'
import { AuditResultBadge } from './AuditResultBadge'

/** Cột dùng chung cho trang Nhật ký và khối nhật ký trên Dashboard Super Admin (SRS 3.1.8). */
export function useAuditColumns(onView: (a: AuditLog) => void, compact = false): Column<AuditLog>[] {
  const { t } = useTranslation()
  const cols: Column<AuditLog>[] = [
    { key: 'time', header: t('audit.time'), cell: (a) => <span className="text-[13px] whitespace-nowrap tabular">{fmtDateTime(a.createdAt)}</span> },
    { key: 'actor', header: t('audit.actor'), cell: (a) => <span className="font-medium">{a.actor}</span> },
    { key: 'action', header: t('audit.action'), cell: (a) => <Badge variant={AUDIT_TONE[a.actionType] ?? 'neutral'}>{t(`auditAction.${a.actionType}`, { defaultValue: a.actionType })}</Badge> },
    {
      key: 'entity',
      header: t('audit.entity'),
      cell: (a) => (
        <span className="text-[13px] text-muted-foreground">
          {t(`auditEntity.${a.entityType}`, { defaultValue: a.entityType })}
          {a.entityId && <span className="font-mono"> #{a.entityId}</span>}
        </span>
      ),
    },
    { key: 'summary', header: t('audit.summary'), cell: (a) => <span className="line-clamp-2 text-[13px]">{a.summary}</span> },
    { key: 'result', header: t('audit.result'), cell: (a) => <AuditResultBadge result={a.result} /> },
  ]
  if (!compact) cols.push({ key: 'ip', header: 'IP', cell: (a) => <span className="font-mono text-xs text-muted-foreground">{a.ip}</span> })
  cols.push({
    key: 'view',
    header: '',
    width: '48px',
    cell: (a) => (
      <Button
        variant="ghost"
        size="icon-sm"
        aria-label={t('audit.view')}
        onClick={(e) => {
          e.stopPropagation()
          onView(a)
        }}
      >
        <Eye />
      </Button>
    ),
  })
  return cols
}

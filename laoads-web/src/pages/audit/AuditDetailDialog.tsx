import { useTranslation } from 'react-i18next'
import type { AuditLog } from '@/api/types'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { fmtDateTime } from '@/lib/format'
import { AuditResultBadge } from './AuditResultBadge'

/** JSON chi tiết in thụt lề; chuỗi không phải JSON giữ nguyên. */
function pretty(json: string | null) {
  if (!json) return null
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch {
    return json
  }
}

/** Popup chi tiết nhật ký (SRS 3.1.9) — chỉ đọc, dùng chung cho trang Nhật ký và khối nhật ký trên Dashboard. */
export function AuditDetailDialog({ log, onOpenChange }: { log: AuditLog | null; onOpenChange: (open: boolean) => void }) {
  const { t } = useTranslation()
  const rows: [string, React.ReactNode][] = log
    ? [
        [t('audit.time'), fmtDateTime(log.createdAt)],
        [t('audit.actor'), log.actor],
        [t('audit.action'), t(`auditAction.${log.actionType}`, { defaultValue: log.actionType })],
        [t('audit.entity'), `${t(`auditEntity.${log.entityType}`, { defaultValue: log.entityType })}${log.entityId ? ` #${log.entityId}` : ''}`],
        [t('audit.result'), <AuditResultBadge key="r" result={log.result} />],
        ...(log.errorCode ? ([[t('audit.errorCode'), <span key="e" className="font-mono">{log.errorCode}</span>]] as [string, React.ReactNode][]) : []),
        [t('audit.summary'), log.summary ?? '—'],
        ['IP', <span key="ip" className="font-mono">{log.ip ?? '—'}</span>],
      ]
    : []
  const detail = pretty(log?.detailJson ?? null)
  return (
    <Dialog open={!!log} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-2xl">
        <DialogHeader>
          <DialogTitle>{t('audit.detailTitle')}</DialogTitle>
          <DialogDescription>{t('audit.detailDesc')}</DialogDescription>
        </DialogHeader>
        <dl className="grid gap-x-6 gap-y-3 text-sm sm:grid-cols-[140px_1fr]">
          {rows.map(([k, v]) => (
            <div key={k} className="contents">
              <dt className="text-muted-foreground">{k}</dt>
              <dd className="min-w-0 break-words">{v}</dd>
            </div>
          ))}
        </dl>
        {detail && (
          <div>
            <div className="mb-1.5 text-sm text-muted-foreground">{t('audit.detailJson')}</div>
            <pre className="max-h-72 overflow-auto rounded-lg bg-muted/60 p-3 font-mono text-xs leading-relaxed whitespace-pre-wrap">{detail}</pre>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}

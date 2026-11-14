import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Download, ScrollText } from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { AuditLog } from '@/api/types'
import { exportAuditLogs, useAuditLogs } from '@/features/admin/api'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { SearchInput } from '@/components/common/SearchInput'
import { Button } from '@/components/ui/button'
import { Badge, type BadgeVariant } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { fmtDateTime } from '@/lib/format'

const ACTIONS = ['LOGIN', 'CREATE', 'UPDATE', 'DELETE', 'LOCK', 'UNLOCK', 'APPROVE', 'REJECT', 'SUBMIT', 'CHANGE_ROLE', 'RESET_PASSWORD', 'IMPORT']
const ENTITIES = ['AUTH', 'USER', 'WORKSPACE', 'MEMBER', 'ROLE', 'PRICE_QUOTE', 'CAMPAIGN', 'SEGMENT']
const TONE: Record<string, BadgeVariant> = { CREATE: 'success', APPROVE: 'success', UNLOCK: 'success', DELETE: 'danger', REJECT: 'danger', LOCK: 'warning', UPDATE: 'info', CHANGE_ROLE: 'info', SUBMIT: 'brand' }

export default function AuditLogPage() {
  const { t } = useTranslation()
  const [page, setPage] = React.useState(0)
  const [f, setF] = React.useState({ actor: '', actionType: '', entityType: '', from: '', to: '' })
  const set = (k: keyof typeof f, v: string) => {
    setF((x) => ({ ...x, [k]: v }))
    setPage(0)
  }
  const list = useAuditLogs({ page, size: 20, ...f })
  const [exporting, setExporting] = React.useState(false)

  const columns: Column<AuditLog>[] = [
    { key: 'time', header: t('audit.time'), cell: (a) => <span className="text-[13px] whitespace-nowrap tabular">{fmtDateTime(a.createdAt)}</span> },
    { key: 'actor', header: t('audit.actor'), cell: (a) => <span className="font-medium">{a.actor}</span> },
    { key: 'action', header: t('audit.action'), cell: (a) => <Badge variant={TONE[a.actionType] ?? 'neutral'}>{t(`auditAction.${a.actionType}`, { defaultValue: a.actionType })}</Badge> },
    { key: 'entity', header: t('audit.entity'), cell: (a) => <span className="text-[13px] text-muted-foreground">{t(`auditEntity.${a.entityType}`, { defaultValue: a.entityType })}{a.entityId && <span className="font-mono"> #{a.entityId}</span>}</span> },
    { key: 'summary', header: t('audit.summary'), cell: (a) => <span className="text-[13px]">{a.summary}</span> },
    { key: 'ip', header: 'IP', cell: (a) => <span className="font-mono text-xs text-muted-foreground">{a.ip}</span> },
  ]

  return (
    <>
      <PageHeader
        icon={<ScrollText />}
        title={t('nav.auditLogs')}
        description={t('audit.desc')}
        actions={
          <Button
            variant="outline"
            loading={exporting}
            onClick={async () => {
              setExporting(true)
              try {
                await exportAuditLogs(f)
              } catch (e) {
                toast.error(errorMessage(e))
              } finally {
                setExporting(false)
              }
            }}
          >
            <Download />
            {t('common.exportExcel')}
          </Button>
        }
      />
      <DataTable
        dense
        toolbar={
          <>
            <SearchInput value={f.actor} onChange={(v) => set('actor', v)} placeholder={t('audit.actorPh')} className="sm:w-56" />
            <Select className="w-40" value={f.actionType} onValueChange={(v) => set('actionType', v)} allLabel={t('audit.allActions')} options={ACTIONS.map((a) => ({ value: a, label: t(`auditAction.${a}`) }))} />
            <Select className="w-40" value={f.entityType} onValueChange={(v) => set('entityType', v)} allLabel={t('audit.allEntities')} options={ENTITIES.map((a) => ({ value: a, label: t(`auditEntity.${a}`) }))} />
            <div className="ml-auto flex items-center gap-2 text-sm text-muted-foreground">
              <Input type="date" className="w-40" value={f.from} onChange={(e) => set('from', e.target.value)} aria-label={t('common.from')} />
              →
              <Input type="date" className="w-40" value={f.to} onChange={(e) => set('to', e.target.value)} aria-label={t('common.to')} />
            </div>
          </>
        }
        columns={columns}
        rows={list.data?.items}
        rowKey={(a) => a.id}
        loading={list.isLoading}
        error={list.isError ? errorMessage(list.error) : undefined}
        meta={list.data?.meta}
        onPageChange={setPage}
      />
    </>
  )
}

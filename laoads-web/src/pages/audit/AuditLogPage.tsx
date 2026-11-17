import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Download, ScrollText } from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { AuditLog } from '@/api/types'
import { exportAuditLogs, useAuditLogs } from '@/features/admin/api'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable } from '@/components/common/DataTable'
import { SearchInput } from '@/components/common/SearchInput'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { AuditDetailDialog } from './AuditDetailDialog'
import { AUDIT_ACTIONS, AUDIT_ENTITIES, AUDIT_RESULTS } from './auditMeta'
import { useAuditColumns } from './auditColumns'

export default function AuditLogPage() {
  const { t } = useTranslation()
  const [page, setPage] = React.useState(0)
  const [size, setSize] = React.useState(20)
  const [f, setF] = React.useState({ actor: '', actionType: '', entityType: '', result: '', from: '', to: '' })
  const [viewing, setViewing] = React.useState<AuditLog | null>(null)
  const set = (k: keyof typeof f, v: string) => {
    setF((x) => ({ ...x, [k]: v }))
    setPage(0)
  }
  const list = useAuditLogs({ page, size, ...f })
  const [exporting, setExporting] = React.useState(false)
  const columns = useAuditColumns(setViewing)

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
            <Select className="w-40" value={f.actionType} onValueChange={(v) => set('actionType', v)} allLabel={t('audit.allActions')} options={AUDIT_ACTIONS.map((a) => ({ value: a, label: t(`auditAction.${a}`) }))} />
            <Select className="w-40" value={f.entityType} onValueChange={(v) => set('entityType', v)} allLabel={t('audit.allEntities')} options={AUDIT_ENTITIES.map((a) => ({ value: a, label: t(`auditEntity.${a}`) }))} />
            <Select className="w-36" value={f.result} onValueChange={(v) => set('result', v)} allLabel={t('audit.allResults')} options={AUDIT_RESULTS.map((r) => ({ value: r, label: t(`auditResult.${r}`) }))} />
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
        onPageSizeChange={(s) => {
          setSize(s)
          setPage(0)
        }}
        onRowClick={setViewing}
      />
      <AuditDetailDialog log={viewing} onOpenChange={(o) => !o && setViewing(null)} />
    </>
  )
}

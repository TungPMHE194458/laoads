import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { Database, FileSpreadsheet, Plus, Sparkles, Trash2, UsersRound, Zap } from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { Segment, SegmentStatus, SegmentType } from '@/api/types'
import { useDeleteSegments, useSegments } from '@/features/campaign/api'
import { useCan } from '@/features/auth/permissions'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { SearchInput } from '@/components/common/SearchInput'
import { SegmentStatusBadge } from '@/components/common/status'
import { ConfirmDialog } from '@/components/common/dialogs'
import { EmptyState } from '@/components/common/EmptyState'
import { Button } from '@/components/ui/button'
import { Select } from '@/components/ui/select'
import { fmtDateTime, fmtNumber } from '@/lib/format'
import { CreateSegmentDialog } from './CreateSegmentDialog'

export const SEGMENT_ICON: Record<SegmentType, typeof Database> = { QUERY: Database, FILE: FileSpreadsheet, EVENT: Zap, CAMPAIGN_OUTCOME: Sparkles }

export default function SegmentsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const can = useCan()
  const [sp, setSp] = useSearchParams()
  const [page, setPage] = React.useState(0)
  const [query, setQuery] = React.useState('')
  const [type, setType] = React.useState('')
  const [status, setStatus] = React.useState('')
  const [selected, setSelected] = React.useState<(string | number)[]>([])
  const [confirm, setConfirm] = React.useState(false)
  const list = useSegments({ page, size: 10, query, type, status })
  const del = useDeleteSegments()
  const createOpen = sp.get('create') === '1'
  const setCreate = (o: boolean) => {
    const n = new URLSearchParams(sp)
    if (o) n.set('create', '1')
    else n.delete('create')
    setSp(n, { replace: true })
  }
  // Đổi filter/trang → về trang đầu + bỏ lựa chọn (quy ước bulk-delete)
  const refilter = (fn: () => void) => {
    fn()
    setPage(0)
    setSelected([])
  }

  const columns: Column<Segment>[] = [
    {
      key: 'name',
      header: t('segment.name'),
      cell: (s) => {
        const I = SEGMENT_ICON[s.type]
        return (
          <div className="flex min-w-64 items-center gap-3">
            <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-accent text-brand-600">
              <I className="size-4" />
            </span>
            <div className="min-w-0">
              <div className="truncate font-medium">{s.name}</div>
              <div className="truncate text-xs text-muted-foreground">{s.description ?? s.fileName ?? '—'}</div>
            </div>
          </div>
        )
      },
    },
    { key: 'type', header: t('segment.type'), cell: (s) => <span className="text-[13px]">{t(`segmentType.${s.type}`)}</span> },
    { key: 'count', header: t('segment.count'), align: 'right', cell: (s) => <span className="font-medium tabular">{s.status === 'BUILDING' ? '…' : fmtNumber(s.totalUsers)}</span> },
    { key: 'status', header: t('common.status'), cell: (s) => <SegmentStatusBadge status={s.status} /> },
    { key: 'used', header: t('segment.usedBy'), align: 'right', cell: (s) => <span className="text-[13px] tabular">{s.referencedBy}</span> },
    { key: 'created', header: t('common.createdAt'), cell: (s) => <span className="text-[13px] tabular text-foreground/80">{fmtDateTime(s.createdAt)}<span className="block text-xs text-muted-foreground">{s.createdBy}</span></span> },
  ]

  return (
    <>
      <PageHeader
        icon={<UsersRound />}
        title={t('nav.segments')}
        description={t('segment.desc')}
        actions={
          can('SEGMENT_MANAGE') && (
            <Button onClick={() => setCreate(true)}>
              <Plus />
              {t('segment.create')}
            </Button>
          )
        }
      />
      <DataTable
        toolbar={
          selected.length > 0 ? (
            <>
              <span className="text-sm font-medium">{t('table.selected', { n: selected.length })}</span>
              <Button variant="ghost" size="sm" onClick={() => setSelected([])}>
                {t('common.clear')}
              </Button>
              <Button variant="destructive" size="sm" className="ml-auto" onClick={() => setConfirm(true)}>
                <Trash2 />
                {t('common.delete')}
              </Button>
            </>
          ) : (
            <>
              <SearchInput value={query} onChange={(v) => refilter(() => setQuery(v))} placeholder={t('segment.searchPh')} />
              <div className="ml-auto flex gap-2">
                <Select className="w-44" value={type} onValueChange={(v) => refilter(() => setType(v))} allLabel={t('segment.allTypes')} options={(['QUERY', 'FILE', 'EVENT', 'CAMPAIGN_OUTCOME'] as SegmentType[]).map((x) => ({ value: x, label: t(`segmentType.${x}`) }))} />
                <Select className="w-40" value={status} onValueChange={(v) => refilter(() => setStatus(v))} allLabel={t('common.allStatus')} options={(['BUILDING', 'READY', 'FAILED', 'ARCHIVED'] as SegmentStatus[]).map((x) => ({ value: x, label: t(`segmentStatus.${x}`) }))} />
              </div>
            </>
          )
        }
        columns={columns}
        rows={list.data?.items}
        rowKey={(s) => s.id}
        loading={list.isLoading}
        meta={list.data?.meta}
        onPageChange={(p) => refilter(() => setPage(p))}
        onRowClick={(s) => navigate(`/segments/${s.id}`)}
        selectable={can('SEGMENT_MANAGE')}
        selectedIds={selected}
        onSelectionChange={setSelected}
        isRowSelectable={(s) => s.referencedBy === 0}
        empty={<EmptyState icon={<UsersRound />} title={t('segment.emptyTitle')} description={t('segment.emptyDesc')} />}
      />
      {createOpen && <CreateSegmentDialog open onOpenChange={setCreate} />}
      <ConfirmDialog
        open={confirm}
        onOpenChange={setConfirm}
        destructive
        title={t('segment.deleteTitle', { n: selected.length })}
        description={t('segment.deleteDesc')}
        confirmLabel={t('common.delete')}
        loading={del.isPending}
        onConfirm={() =>
          del.mutate(selected as number[], {
            onSuccess: (r) => {
              if (r.failed.length) toast.warning(t('bulk.partial', { ok: r.deleted, fail: r.failed.length }))
              else toast.success(t('bulk.done', { n: r.deleted }))
              setSelected([])
              setConfirm(false)
            },
            onError: (e) => toast.error(errorMessage(e)),
          })
        }
      />
    </>
  )
}

import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { Database, FileSpreadsheet, Loader2, Plus, RotateCcw, ServerCog, Sparkles, Trash2, UsersRound, Zap } from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { Segment, SegmentStatus, SegmentType } from '@/api/types'
import { useDeleteSegments, useRetrySegment, useSegments } from '@/features/campaign/api'
import { RETRYABLE_SEGMENT_TYPES } from '@/features/campaign/audience'
import { useCan } from '@/features/auth/permissions'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { SearchInput } from '@/components/common/SearchInput'
import { SegmentStatusBadge } from '@/components/common/status'
import { ConfirmDialog } from '@/components/common/dialogs'
import { EmptyState } from '@/components/common/EmptyState'
import { Button } from '@/components/ui/button'
import { Select } from '@/components/ui/select'
import { Tooltip } from '@/components/ui/misc'
import { fmtDateTime, fmtNumber } from '@/lib/format'
import { CreateSegmentDialog } from './CreateSegmentDialog'

export const SEGMENT_ICON: Record<SegmentType, typeof Database> = { QUERY: Database, FILE: FileSpreadsheet, EVENT: Zap, CAMPAIGN_OUTCOME: Sparkles, AUDIENCE: ServerCog }

export default function SegmentsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const can = useCan()
  const [sp, setSp] = useSearchParams()
  const [page, setPage] = React.useState(0)
  const [size, setSize] = React.useState(10)
  const [query, setQuery] = React.useState('')
  const [type, setType] = React.useState('')
  const [status, setStatus] = React.useState('')
  const [selected, setSelected] = React.useState<(string | number)[]>([])
  const [confirm, setConfirm] = React.useState(false)
  const list = useSegments({ page, size, query, type, status })
  // xoá một dòng (SRS 3.5.2) dùng chung API xoá nhiều
  const [rowDelete, setRowDelete] = React.useState<Segment | null>(null)
  const del = useDeleteSegments()
  const retry = useRetrySegment()
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
    { key: 'id', header: 'ID', width: '64px', cell: (s) => <span className="text-muted-foreground tabular">{s.id}</span> },
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
              <div className="truncate text-xs text-muted-foreground">
                {s.description ?? s.fileName ?? (s.audience ? `${t(`segmentSource.${s.source}`)} · ${s.audience.tableName}` : '—')}
              </div>
            </div>
          </div>
        )
      },
    },
    { key: 'type', header: t('segment.type'), cell: (s) => <span className="text-[13px]">{t(`segmentType.${s.type}`)}</span> },
    {
      key: 'count',
      header: t('segment.count'),
      align: 'right',
      cell: (s) =>
        s.status === 'BUILDING' ? (
          // tệp AUDIENCE nạp dần: hiện số đã nạp tới lúc này
          <span className="inline-flex items-center gap-1.5 font-medium tabular text-muted-foreground" title={t('segment.loadingSoFar')}>
            <Loader2 className="size-3.5 animate-spin" />
            {s.totalUsers > 0 ? fmtNumber(s.totalUsers) : '…'}
          </span>
        ) : (
          <span className="font-medium tabular">{fmtNumber(s.totalUsers)}</span>
        ),
    },
    {
      key: 'status',
      header: t('common.status'),
      cell: (s) => (
        <div className="flex max-w-60 flex-col items-start gap-1">
          <SegmentStatusBadge status={s.status} />
          {s.status === 'FAILED' && s.errorMessage && (
            <span className="line-clamp-2 text-xs text-danger" title={s.errorMessage}>
              {s.errorMessage}
            </span>
          )}
          {s.status === 'FAILED' && RETRYABLE_SEGMENT_TYPES.includes(s.type) && can('SEGMENT_MANAGE') && (
            <Button
              variant="outline"
              size="sm"
              className="h-7"
              loading={retry.isPending && retry.variables === s.id}
              onClick={(e) => {
                e.stopPropagation()
                retry.mutate(s.id, {
                  onSuccess: () => toast.success(t('segment.retryStarted')),
                  onError: (er) => toast.error(errorMessage(er)),
                })
              }}
            >
              <RotateCcw />
              {t('segment.retry')}
            </Button>
          )}
        </div>
      ),
    },
    { key: 'used', header: t('segment.usedBy'), align: 'right', cell: (s) => <span className="text-[13px] tabular">{s.referencedBy}</span> },
    { key: 'created', header: t('common.createdAt'), cell: (s) => <span className="text-[13px] tabular text-foreground/80">{fmtDateTime(s.createdAt)}<span className="block text-xs text-muted-foreground">{s.createdBy}</span></span> },
  ]
  if (can('SEGMENT_MANAGE'))
    columns.push({
      key: 'delete',
      header: '',
      width: '48px',
      cell: (s) => (
        <Tooltip content={s.referencedBy > 0 ? t('segment.inUseNoDelete') : t('common.delete')}>
          <span>
            <Button
              variant="ghost"
              size="icon-sm"
              aria-label={t('common.delete')}
              disabled={s.referencedBy > 0}
              className="text-muted-foreground hover:text-danger"
              onClick={(e) => {
                e.stopPropagation()
                setRowDelete(s)
              }}
            >
              <Trash2 />
            </Button>
          </span>
        </Tooltip>
      ),
    })

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
                <Select className="w-44" value={type} onValueChange={(v) => refilter(() => setType(v))} allLabel={t('segment.allTypes')} options={(['QUERY', 'FILE', 'AUDIENCE', 'EVENT', 'CAMPAIGN_OUTCOME'] as SegmentType[]).map((x) => ({ value: x, label: t(`segmentType.${x}`) }))} />
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
        error={list.isError ? errorMessage(list.error) : undefined}
        onPageChange={(p) => refilter(() => setPage(p))}
        onPageSizeChange={(n) => refilter(() => setSize(n))}
        onRowClick={(s) => navigate(`/segments/${s.id}`)}
        selectable={can('SEGMENT_MANAGE')}
        selectedIds={selected}
        onSelectionChange={setSelected}
        isRowSelectable={(s) => s.referencedBy === 0}
        empty={
          query || type || status ? (
            <EmptyState icon={<UsersRound />} title={t('common.noResult')} description={t('common.noResultDesc')} />
          ) : (
            <EmptyState icon={<UsersRound />} title={t('segment.emptyTitle')} description={t('segment.emptyDesc')} />
          )
        }
      />
      {createOpen && <CreateSegmentDialog open onOpenChange={setCreate} />}
      <ConfirmDialog
        open={!!rowDelete}
        onOpenChange={(o) => !o && setRowDelete(null)}
        destructive
        title={t('segment.deleteOneTitle')}
        description={t('segment.deleteOneDesc', { name: rowDelete?.name })}
        confirmLabel={t('common.delete')}
        loading={del.isPending}
        onConfirm={() =>
          rowDelete &&
          del.mutate([rowDelete.id], {
            onSuccess: (r) => {
              if (r.failed.length) toast.error(r.failed[0].reason)
              else toast.success(t('bulk.done', { n: r.deleted }))
              setRowDelete(null)
            },
            onError: (e) => toast.error(errorMessage(e)),
          })
        }
      />
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

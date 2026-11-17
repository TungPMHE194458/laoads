import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ArrowDown, ArrowUp, ArrowUpDown, ChevronDown, FlaskConical, FolderKanban, FolderPlus, Megaphone, Pencil, Plus, Rocket, Trash2, Zap } from 'lucide-react'
import { toast } from 'sonner'
import { errorMessage } from '@/api/client'
import type { CampaignGroup, CampaignStatus, CampaignSummary } from '@/api/types'
import { useCampaignGroups, useCampaigns, useDeleteCampaignGroup } from '@/features/campaign/api'
import { ConfirmDialog } from '@/components/common/dialogs'
import { useCan } from '@/features/auth/permissions'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { SearchInput } from '@/components/common/SearchInput'
import { CampaignStatusBadge, ChannelChips } from '@/components/common/status'
import { EmptyState } from '@/components/common/EmptyState'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Select } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/misc'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { fmtCompact, fmtDate, fmtDateTime, fmtNumber, fmtPercent } from '@/lib/format'
import { CampaignRowMenu } from './campaignActions'
import { CampaignGroupDialog } from './CampaignGroupDialog'

const STATUSES: CampaignStatus[] = ['DRAFT', 'PENDING_APPROVAL', 'SCHEDULED', 'RUNNING', 'PAUSED', 'COMPLETED', 'REJECTED', 'CANCELLED']

/** Tiêu đề cột bấm để sắp xếp (SRS 3.2.7.1): lần 1 giảm dần, lần 2 tăng dần. */
function SortHeader({ label, field, sort, dir, onSort }: { label: string; field: string; sort: string; dir: string; onSort: (field: string, dir: 'asc' | 'desc') => void }) {
  const active = sort === field
  const Icon = !active ? ArrowUpDown : dir === 'asc' ? ArrowUp : ArrowDown
  return (
    <button
      type="button"
      onClick={() => onSort(field, active && dir === 'desc' ? 'asc' : 'desc')}
      className="inline-flex cursor-pointer items-center gap-1 uppercase hover:text-foreground"
      aria-sort={active ? (dir === 'asc' ? 'ascending' : 'descending') : 'none'}
    >
      {label}
      <Icon className={`size-3 ${active ? 'text-foreground' : 'opacity-50'}`} />
    </button>
  )
}

export function CreateCampaignButton() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button>
          <Plus />
          {t('campaign.create')}
          <ChevronDown className="opacity-80" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-72">
        <DropdownMenuItem onSelect={() => navigate('/campaigns/new?mode=OFFICIAL')} className="items-start py-2.5">
          <Rocket className="mt-0.5 text-brand-600!" />
          <span>
            <span className="block font-medium">{t('campaign.mode.OFFICIAL')}</span>
            <span className="block text-xs text-muted-foreground">{t('campaign.mode.OFFICIAL_desc')}</span>
          </span>
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={() => navigate('/campaigns/new?mode=TEST')} className="items-start py-2.5">
          <FlaskConical className="mt-0.5 text-info!" />
          <span>
            <span className="block font-medium">{t('campaign.mode.TEST')}</span>
            <span className="block text-xs text-muted-foreground">{t('campaign.mode.TEST_desc')}</span>
          </span>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

/** Tab "Nhóm chiến dịch" (như laoads.com): bảng tên / mô tả / số chiến dịch; tạo, sửa, xoá nhóm trống. */
function CampaignGroupsTab({ onOpenGroup }: { onOpenGroup: (g: CampaignGroup) => void }) {
  const { t } = useTranslation()
  const can = useCan()
  const groups = useCampaignGroups()
  const del = useDeleteCampaignGroup()
  const [query, setQuery] = React.useState('')
  const [editing, setEditing] = React.useState<CampaignGroup | null | undefined>(undefined)
  const [deleting, setDeleting] = React.useState<CampaignGroup | null>(null)
  const q = query.trim().toLowerCase()
  const rows = (groups.data ?? []).filter((g) => !q || g.name.toLowerCase().includes(q) || (g.description ?? '').toLowerCase().includes(q))
  const columns: Column<CampaignGroup>[] = [
    { key: 'no', header: t('common.no'), width: '56px', align: 'right', cell: (_g, i) => <span className="text-muted-foreground tabular">{i + 1}</span> },
    {
      key: 'name',
      header: t('campaign.groupDialog.name'),
      cell: (g) => (
        <div className="flex min-w-48 items-center gap-3">
          <span className="grid size-9 shrink-0 place-items-center rounded-lg bg-accent text-brand-600">
            <FolderKanban className="size-4" />
          </span>
          <span className="font-medium">{g.name}</span>
        </div>
      ),
    },
    { key: 'desc', header: t('campaign.groupDialog.description'), cell: (g) => <span className="line-clamp-2 max-w-md text-[13px] text-muted-foreground">{g.description || '—'}</span> },
    { key: 'count', header: t('campaign.groupTotal'), align: 'right', cell: (g) => <span className="font-semibold tabular">{fmtNumber(g.campaignCount)}</span> },
    { key: 'running', header: t('campaignStatus.RUNNING'), align: 'right', cell: (g) => <span className="tabular text-success">{fmtNumber(g.runningCount)}</span> },
    { key: 'createdAt', header: t('common.createdAt'), cell: (g) => <span className="text-[13px] whitespace-nowrap tabular text-muted-foreground">{fmtDate(g.createdAt)}</span> },
    {
      key: 'menu',
      header: '',
      width: '96px',
      align: 'right',
      cell: (g) => (
        <div className="flex justify-end gap-1" onClick={(e) => e.stopPropagation()}>
          {can('CAMPAIGN_UPDATE') && (
            <Button variant="ghost" size="icon-sm" aria-label={t('common.edit')} onClick={() => setEditing(g)}>
              <Pencil />
            </Button>
          )}
          {can('CAMPAIGN_DELETE') && (
            <Button
              variant="ghost"
              size="icon-sm"
              className="text-muted-foreground hover:text-danger"
              aria-label={t('common.delete')}
              disabled={g.campaignCount > 0}
              title={g.campaignCount > 0 ? t('campaign.groupDialog.inUse') : undefined}
              onClick={() => setDeleting(g)}
            >
              <Trash2 />
            </Button>
          )}
        </div>
      ),
    },
  ]
  return (
    <>
      <DataTable
        toolbar={
          <>
            <SearchInput value={query} onChange={setQuery} placeholder={t('campaign.groupDialog.searchPh')} />
            {can('CAMPAIGN_CREATE') && (
              <Button className="ml-auto" onClick={() => setEditing(null)}>
                <FolderPlus />
                {t('campaign.wizard.newGroup')}
              </Button>
            )}
          </>
        }
        columns={columns}
        rows={rows}
        rowKey={(g) => g.id}
        loading={groups.isLoading}
        error={groups.isError ? errorMessage(groups.error) : undefined}
        onRowClick={onOpenGroup}
        empty={<EmptyState icon={<FolderKanban />} title={q ? t('common.noResult') : t('campaign.groupDialog.emptyTitle')} description={q ? t('common.noResultDesc') : t('campaign.groupDialog.emptyDesc')} />}
      />
      {editing !== undefined && <CampaignGroupDialog group={editing} onOpenChange={(o) => !o && setEditing(undefined)} />}
      <ConfirmDialog
        open={!!deleting}
        onOpenChange={(o) => !o && setDeleting(null)}
        destructive
        title={t('campaign.groupDialog.deleteTitle')}
        description={t('campaign.groupDialog.deleteDesc', { name: deleting?.name ?? '' })}
        confirmLabel={t('common.delete')}
        loading={del.isPending}
        onConfirm={() =>
          deleting &&
          del.mutate(deleting.id, {
            onSuccess: () => {
              toast.success(t('campaign.groupDialog.deleted'))
              setDeleting(null)
            },
            onError: (e) => toast.error(errorMessage(e)),
          })
        }
      />
    </>
  )
}

export default function CampaignsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const can = useCan()
  const [sp, setSp] = useSearchParams()
  const [page, setPage] = React.useState(0)
  const [size, setSize] = React.useState(10)
  const query = sp.get('q') ?? ''
  const status = sp.get('status') ?? ''
  const groupId = sp.get('group') ?? ''
  const tab = sp.get('tab') ?? 'campaigns'
  const sort = sp.get('sort') ?? 'createdAt'
  const dir = sp.get('dir') === 'asc' ? 'asc' : 'desc'
  const onSort = (field: string, d: 'asc' | 'desc') => {
    const next = new URLSearchParams(sp)
    next.set('sort', field)
    next.set('dir', d)
    setSp(next, { replace: true })
    setPage(0)
  }
  const sortHeader = (label: string, field: string) => <SortHeader label={label} field={field} sort={sort} dir={dir} onSort={onSort} />

  const setParam = (k: string, v: string) => {
    const next = new URLSearchParams(sp)
    if (v) next.set(k, v)
    else next.delete(k)
    setSp(next, { replace: true })
    setPage(0)
  }

  const list = useCampaigns({ page, size, query, status, groupId: groupId ? Number(groupId) : '', sort, dir })
  const groups = useCampaignGroups()

  const columns: Column<CampaignSummary>[] = [
    { key: 'no', header: t('common.no'), width: '56px', align: 'right', cell: (_c, i) => <span className="text-muted-foreground tabular">{page * size + i + 1}</span> },
    {
      key: 'name',
      header: t('campaign.col.name'),
      cell: (c) => (
        <div className="flex min-w-64 items-center gap-3">
          <span className={`grid size-9 shrink-0 place-items-center rounded-lg ${c.mode === 'TEST' ? 'bg-info-soft text-info' : 'bg-accent text-brand-600'}`}>
            {c.type === 'EVENT' ? <Zap className="size-4" /> : c.mode === 'TEST' ? <FlaskConical className="size-4" /> : <Rocket className="size-4" />}
          </span>
          <div className="min-w-0">
            <div className="truncate font-medium">{c.name}</div>
            <div className="mt-0.5 flex items-center gap-2 text-xs text-muted-foreground">
              <span className="font-mono">{c.code}</span>
              {c.groupName && (
                <span className="inline-flex items-center gap-1">
                  <FolderKanban className="size-3" />
                  {c.groupName}
                </span>
              )}
              {c.mode === 'TEST' && <Badge variant="info">{t('campaign.mode.TEST')}</Badge>}
            </div>
          </div>
        </div>
      ),
    },
    {
      key: 'segment',
      header: t('campaign.col.segment'),
      cell: (c) => (
        <div className="max-w-56">
          <div className="truncate text-[13px]">{c.segmentName ?? '—'}</div>
          <div className="text-xs text-muted-foreground tabular">{t('campaign.customers', { n: fmtNumber(c.targetCount) })}</div>
        </div>
      ),
    },
    { key: 'channels', header: t('campaign.col.channels'), cell: (c) => <ChannelChips channels={c.channels} /> },
    { key: 'status', header: t('common.status'), cell: (c) => <CampaignStatusBadge status={c.status} /> },
    { key: 'startAt', header: sortHeader(t('campaign.col.startAt'), 'startAt'), cell: (c) => <span className="text-[13px] whitespace-nowrap tabular text-foreground/80">{fmtDateTime(c.startAt)}</span> },
    { key: 'endAt', header: sortHeader(t('campaign.col.endAt'), 'endAt'), cell: (c) => <span className="text-[13px] whitespace-nowrap tabular text-foreground/80">{c.endAt ? fmtDateTime(c.endAt) : '—'}</span> },
    { key: 'createdAt', header: sortHeader(t('common.createdAt'), 'createdAt'), cell: (c) => <span className="text-[13px] whitespace-nowrap tabular text-muted-foreground">{fmtDateTime(c.createdAt)}</span> },
    {
      key: 'perf',
      header: t('campaign.col.performance'),
      align: 'right',
      cell: (c) =>
        ['RUNNING', 'PAUSED', 'COMPLETED'].includes(c.status) ? (
          <div className="text-right">
            <div className="font-semibold tabular">{fmtPercent(c.conversionRate)}</div>
            <div className="text-xs text-muted-foreground tabular">{t('campaign.sentShort', { v: fmtPercent(c.sentRate, 0) })}</div>
          </div>
        ) : (
          <span className="text-xs text-muted-foreground">{t('campaign.estCost', { v: fmtCompact(c.expectedCost) })}</span>
        ),
    },
    { key: 'menu', header: '', width: '120px', align: 'right', cell: (c) => <CampaignRowMenu c={c} /> },
  ]

  return (
    <>
      <PageHeader
        icon={<Megaphone />}
        title={t('nav.campaigns')}
        description={t('campaign.listDesc')}
        actions={can('CAMPAIGN_CREATE') && <CreateCampaignButton />}
      />
      <Tabs value={tab} onValueChange={(v) => setParam('tab', v === 'campaigns' ? '' : v)}>
        <TabsList className="mb-4">
          <TabsTrigger value="campaigns">
            <Megaphone />
            {t('campaign.tabCampaigns')}
          </TabsTrigger>
          <TabsTrigger value="groups">
            <FolderKanban />
            {t('campaign.tabGroups')}
          </TabsTrigger>
        </TabsList>

        <TabsContent value="campaigns">
          <DataTable
            toolbar={
              <>
                <SearchInput value={query} onChange={(v) => setParam('q', v)} placeholder={t('campaign.searchPh')} />
                <div className="ml-auto flex flex-wrap gap-2">
                  <Select
                    className="w-48"
                    value={groupId}
                    onValueChange={(v) => setParam('group', v)}
                    allLabel={t('campaign.allGroups')}
                    options={(groups.data ?? []).map((g) => ({ value: String(g.id), label: g.name }))}
                  />
                  <Select
                    className="w-44"
                    value={status}
                    onValueChange={(v) => setParam('status', v)}
                    allLabel={t('common.allStatus')}
                    options={STATUSES.map((s) => ({ value: s, label: t(`campaignStatus.${s}`) }))}
                  />
                </div>
              </>
            }
            columns={columns}
            rows={list.data?.items}
            rowKey={(c) => c.id}
            loading={list.isLoading}
            error={list.isError ? errorMessage(list.error) : undefined}
            meta={list.data?.meta}
            onPageChange={setPage}
            onPageSizeChange={(s) => {
              setSize(s)
              setPage(0)
            }}
            onRowClick={(c) => navigate(`/campaigns/${c.id}`)}
            empty={
              <EmptyState
                icon={<Megaphone />}
                title={query || status || groupId ? t('common.noResult') : t('campaign.emptyTitle')}
                description={query || status || groupId ? t('common.noResultDesc') : t('campaign.emptyDesc')}
                action={!query && !status && !groupId && can('CAMPAIGN_CREATE') ? <CreateCampaignButton /> : undefined}
              />
            }
          />
        </TabsContent>

        <TabsContent value="groups">
          <CampaignGroupsTab
            onOpenGroup={(g) => {
              const next = new URLSearchParams()
              next.set('group', String(g.id))
              setSp(next)
            }}
          />
        </TabsContent>
      </Tabs>
    </>
  )
}

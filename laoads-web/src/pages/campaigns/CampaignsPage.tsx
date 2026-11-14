import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ChevronDown, FlaskConical, FolderKanban, Megaphone, Plus, Rocket, Zap } from 'lucide-react'
import type { CampaignStatus, CampaignSummary } from '@/api/types'
import { useCampaignGroups, useCampaigns } from '@/features/campaign/api'
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
import { fmtCompact, fmtDate, fmtNumber, fmtPercent } from '@/lib/format'
import { CampaignRowMenu } from './campaignActions'

const STATUSES: CampaignStatus[] = ['DRAFT', 'PENDING_APPROVAL', 'SCHEDULED', 'RUNNING', 'PAUSED', 'COMPLETED', 'REJECTED', 'CANCELLED']

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

  const setParam = (k: string, v: string) => {
    const next = new URLSearchParams(sp)
    if (v) next.set(k, v)
    else next.delete(k)
    setSp(next, { replace: true })
    setPage(0)
  }

  const list = useCampaigns({ page, size, query, status, groupId: groupId ? Number(groupId) : '' })
  const groups = useCampaignGroups()

  const columns: Column<CampaignSummary>[] = [
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
    {
      key: 'time',
      header: t('campaign.col.time'),
      cell: (c) => (
        <span className="text-[13px] whitespace-nowrap tabular text-foreground/80">
          {fmtDate(c.startAt)} <span className="text-muted-foreground">→</span> {fmtDate(c.endAt)}
        </span>
      ),
    },
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
    { key: 'menu', header: '', width: '48px', cell: (c) => <CampaignRowMenu c={c} /> },
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
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
            {(groups.data ?? []).map((g) => (
              <button
                key={g.id}
                onClick={() => {
                  const next = new URLSearchParams()
                  next.set('group', String(g.id))
                  setSp(next)
                }}
                className="group cursor-pointer rounded-xl border bg-card p-5 text-left shadow-card transition hover:-translate-y-0.5 hover:border-brand-300"
              >
                <div className="flex items-center gap-3">
                  <span className="grid size-10 place-items-center rounded-xl bg-accent text-brand-600">
                    <FolderKanban className="size-5" />
                  </span>
                  <div className="min-w-0">
                    <div className="truncate font-semibold">{g.name}</div>
                    <div className="text-xs text-muted-foreground">
                      {t('common.createdAt')} {fmtDate(g.createdAt)}
                    </div>
                  </div>
                </div>
                <div className="mt-5 grid grid-cols-2 gap-3 border-t pt-4">
                  <div>
                    <div className="text-xl font-semibold tabular">{g.campaignCount}</div>
                    <div className="text-xs text-muted-foreground">{t('campaign.groupTotal')}</div>
                  </div>
                  <div>
                    <div className="text-xl font-semibold tabular text-success">{g.runningCount}</div>
                    <div className="text-xs text-muted-foreground">{t('campaignStatus.RUNNING')}</div>
                  </div>
                </div>
              </button>
            ))}
          </div>
        </TabsContent>
      </Tabs>
    </>
  )
}

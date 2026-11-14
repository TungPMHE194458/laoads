import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useSearchParams } from 'react-router-dom'
import { toast } from 'sonner'
import { BadgeCheck, Check, ExternalLink, X } from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { CampaignSummary } from '@/api/types'
import { useApprovals, useCampaign, useReviewCampaign } from '@/features/campaign/api'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { SearchInput } from '@/components/common/SearchInput'
import { ChannelChips } from '@/components/common/status'
import { EmptyState } from '@/components/common/EmptyState'
import { Field } from '@/components/common/dialogs'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Select } from '@/components/ui/select'
import { Textarea } from '@/components/ui/input'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, SheetContent } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/misc'
import { fmtDateTime, fmtKip, fmtNumber, fmtRelative } from '@/lib/format'
import { actionIcon } from '@/pages/campaigns/wizard/actionMeta'

function Preview({ id, onDecide }: { id: number; onDecide: (d: 'approve' | 'reject') => void }) {
  const { t } = useTranslation()
  const { data: c, isLoading } = useCampaign(id)
  if (isLoading || !c) return <Skeleton className="m-6 h-80" />
  return (
    <>
      <div className="border-b px-6 py-5 pr-12">
        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <span className="font-mono">{c.code}</span>·<Badge variant={c.mode === 'TEST' ? 'info' : 'brand'}>{t(`campaign.mode.${c.mode}`)}</Badge>
        </div>
        <h2 className="mt-1.5 text-lg font-semibold">{c.name}</h2>
        <p className="mt-1 text-sm text-muted-foreground">
          {t('campaign.by', { who: c.createdBy })} · {fmtRelative(c.createdAt)}
        </p>
      </div>
      <div className="flex-1 overflow-y-auto px-6 py-5 scrollbar-thin">
        <dl className="grid grid-cols-2 gap-4 text-sm">
          {[
            [t('campaign.col.segment'), c.segmentName],
            [t('metric.target'), fmtNumber(c.targetCount)],
            [t('campaign.wizard.expectedCost'), fmtKip(c.expectedCost)],
            [t('campaign.wizard.maxBudget'), c.budget.max ? fmtKip(c.budget.max) : '—'],
            [t('campaign.wizard.startAt'), fmtDateTime(c.schedule.startAt)],
            [t('campaign.wizard.endAt'), fmtDateTime(c.schedule.endAt)],
          ].map(([k, v]) => (
            <div key={k}>
              <dt className="text-xs text-muted-foreground">{k}</dt>
              <dd className="mt-0.5 font-medium">{v}</dd>
            </div>
          ))}
        </dl>
        <div className="mt-5">
          <div className="mb-2 text-xs text-muted-foreground">{t('campaign.col.channels')}</div>
          <ChannelChips channels={c.channels} />
        </div>
        <h3 className="mt-6 mb-3 text-sm font-semibold">{t('approval.contentPreview')}</h3>
        <div className="grid gap-3">
          {c.actionGroups
            .flatMap((g) => g.actions.map((a) => ({ g, a })))
            .map(({ g, a }) => {
              const I = actionIcon(a.type)
              return (
                <div key={a.id} className="rounded-xl border p-3">
                  <div className="flex items-center gap-2 text-[13px] font-medium">
                    <I className="size-4 text-brand-600" />
                    {t(`actionType.${a.type}`)}
                    <span className="ml-auto text-xs font-normal text-muted-foreground">
                      {g.name} · {g.allocationPercent}%
                    </span>
                  </div>
                  {a.content && (
                    <div className="mt-2 max-w-sm rounded-2xl rounded-tl-sm bg-muted px-3.5 py-2.5 text-[13px] whitespace-pre-wrap">
                      {a.senderId && <div className="mb-1 text-[11px] font-semibold text-muted-foreground">{a.senderId}</div>}
                      {a.content}
                    </div>
                  )}
                  {a.templateId && <div className="mt-2 font-mono text-xs text-muted-foreground">template: {a.templateId}</div>}
                </div>
              )
            })}
        </div>
      </div>
      <div className="flex items-center gap-2 border-t px-6 py-4">
        <Button variant="ghost" asChild>
          <Link to={`/campaigns/${c.id}`}>
            <ExternalLink />
            {t('approval.openDetail')}
          </Link>
        </Button>
        <div className="ml-auto flex gap-2">
          <Button variant="outline" className="text-danger hover:text-danger" onClick={() => onDecide('reject')}>
            <X />
            {t('approval.reject')}
          </Button>
          <Button onClick={() => onDecide('approve')}>
            <Check />
            {t('approval.approve')}
          </Button>
        </div>
      </div>
    </>
  )
}

export default function ApprovalsPage() {
  const { t } = useTranslation()
  const [sp, setSp] = useSearchParams()
  const [page, setPage] = React.useState(0)
  const [query, setQuery] = React.useState('')
  const [mode, setMode] = React.useState('')
  const list = useApprovals({ page, size: 10, query, mode })
  const review = useReviewCampaign()
  const focus = Number(sp.get('focus')) || null
  const [decision, setDecision] = React.useState<null | { id: number; d: 'approve' | 'reject' }>(null)
  const [reason, setReason] = React.useState('')

  const setFocus = (id: number | null) => {
    const n = new URLSearchParams(sp)
    if (id) n.set('focus', String(id))
    else n.delete('focus')
    setSp(n, { replace: true })
  }

  const confirm = () => {
    if (!decision) return
    review.mutate(
      { id: decision.id, decision: decision.d, reason },
      {
        onSuccess: () => {
          toast.success(decision.d === 'approve' ? t('approval.approved') : t('approval.rejected'))
          setDecision(null)
          setReason('')
          setFocus(null)
        },
        onError: (e) => toast.error(errorMessage(e)),
      },
    )
  }

  const columns: Column<CampaignSummary>[] = [
    {
      key: 'name',
      header: t('campaign.col.name'),
      cell: (c) => (
        <div className="min-w-56">
          <div className="font-medium">{c.name}</div>
          <div className="mt-0.5 text-xs text-muted-foreground">
            <span className="font-mono">{c.code}</span> · {c.groupName ?? t('campaign.wizard.noGroup')}
          </div>
        </div>
      ),
    },
    { key: 'mode', header: t('approval.type'), cell: (c) => <Badge variant={c.mode === 'TEST' ? 'info' : 'brand'}>{t(`campaign.mode.${c.mode}`)}</Badge> },
    { key: 'seg', header: t('campaign.col.segment'), cell: (c) => <span className="text-[13px]">{c.segmentName}</span> },
    { key: 'target', header: t('metric.target'), align: 'right', cell: (c) => <span className="tabular">{fmtNumber(c.targetCount)}</span> },
    { key: 'cost', header: t('campaign.wizard.expectedCost'), align: 'right', cell: (c) => <span className="tabular">{fmtKip(c.expectedCost)}</span> },
    { key: 'by', header: t('approval.requestedBy'), cell: (c) => <span className="text-[13px]">{c.createdBy}<span className="block text-xs text-muted-foreground">{fmtRelative(c.createdAt)}</span></span> },
    {
      key: 'act',
      header: '',
      align: 'right',
      cell: (c) => (
        <div className="flex justify-end gap-1" onClick={(e) => e.stopPropagation()}>
          <Button size="sm" variant="ghost" className="text-danger hover:text-danger" onClick={() => setDecision({ id: c.id, d: 'reject' })}>
            {t('approval.reject')}
          </Button>
          <Button size="sm" variant="soft" onClick={() => setDecision({ id: c.id, d: 'approve' })}>
            {t('approval.approve')}
          </Button>
        </div>
      ),
    },
  ]

  return (
    <>
      <PageHeader icon={<BadgeCheck />} title={t('nav.approvals')} description={t('approval.desc')} />
      <DataTable
        toolbar={
          <>
            <SearchInput value={query} onChange={(v) => { setQuery(v); setPage(0) }} placeholder={t('campaign.searchPh')} />
            <Select className="ml-auto w-48" value={mode} onValueChange={(v) => { setMode(v); setPage(0) }} allLabel={t('approval.allTypes')} options={(['OFFICIAL', 'TEST'] as const).map((m) => ({ value: m, label: t(`campaign.mode.${m}`) }))} />
          </>
        }
        columns={columns}
        rows={list.data?.items}
        rowKey={(c) => c.id}
        loading={list.isLoading}
        meta={list.data?.meta}
        onPageChange={setPage}
        onRowClick={(c) => setFocus(c.id)}
        empty={<EmptyState icon={<BadgeCheck />} title={t('approval.emptyTitle')} description={t('approval.emptyDesc')} />}
      />

      <Dialog open={!!focus} onOpenChange={(o) => !o && setFocus(null)}>
        <SheetContent aria-describedby={undefined}>
          <DialogTitle className="sr-only">{t('approval.preview')}</DialogTitle>
          {focus && <Preview id={focus} onDecide={(d) => setDecision({ id: focus, d })} />}
        </SheetContent>
      </Dialog>

      <Dialog open={!!decision} onOpenChange={(o) => !o && setDecision(null)}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>{decision?.d === 'approve' ? t('approval.approveTitle') : t('approval.rejectTitle')}</DialogTitle>
            <DialogDescription>{decision?.d === 'approve' ? t('approval.approveDesc') : t('approval.rejectDesc')}</DialogDescription>
          </DialogHeader>
          {decision?.d === 'reject' && (
            <Field label={t('approval.reason')} required>
              <Textarea rows={3} value={reason} onChange={(e) => setReason(e.target.value)} autoFocus />
            </Field>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setDecision(null)}>
              {t('common.cancel')}
            </Button>
            <Button variant={decision?.d === 'reject' ? 'destructive' : 'default'} loading={review.isPending} disabled={decision?.d === 'reject' && !reason.trim()} onClick={confirm}>
              {decision?.d === 'approve' ? t('approval.approve') : t('approval.reject')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  )
}

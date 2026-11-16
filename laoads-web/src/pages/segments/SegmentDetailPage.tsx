import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router-dom'
import { toast } from 'sonner'
import { CircleAlert, Loader2, RotateCcw, UsersRound } from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { SegmentMember } from '@/api/types'
import { useRetrySegment, useSegment, useSegmentMembers } from '@/features/campaign/api'
import { OPERATOR_SYMBOL, PHONE_TARGET, RETRYABLE_SEGMENT_TYPES } from '@/features/campaign/audience'
import { useCan } from '@/features/auth/permissions'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { SegmentStatusBadge } from '@/components/common/status'
import { EmptyState } from '@/components/common/EmptyState'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Skeleton } from '@/components/ui/misc'
import { fmtDateTime, fmtNumber } from '@/lib/format'
import { SEGMENT_ICON } from './SegmentsPage'

export default function SegmentDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const sid = Number(id)
  const seg = useSegment(Number.isFinite(sid) ? sid : null)
  const [page, setPage] = React.useState(0)
  const members = useSegmentMembers(seg.data?.status === 'READY' ? sid : null, page)
  const retry = useRetrySegment()
  const can = useCan()
  const s = seg.data

  if (seg.isLoading) return <Skeleton className="h-96 rounded-xl" />
  if (!s) return <EmptyState icon={<UsersRound />} title={t('status.notFoundTitle')} />
  const Icon = SEGMENT_ICON[s.type]

  const attrKeys = Object.keys(members.data?.items[0]?.attributes ?? {})
  const columns: Column<SegmentMember>[] = [
    { key: 'phone', header: t('segment.phone'), cell: (m) => <span className="font-mono text-[13px]">{m.phone}</span> },
    { key: 'key', header: 'Recipient key', cell: (m) => <span className="font-mono text-xs text-muted-foreground">{m.recipientKey}</span> },
    ...attrKeys.map((k) => ({ key: k, header: t(`attr.${k}`, { defaultValue: k }), cell: (m: SegmentMember) => <span className="text-[13px]">{String(m.attributes[k])}</span> })),
  ]

  return (
    <>
      <PageHeader crumbs={[{ label: t('nav.segments'), to: '/segments' }, { label: s.name }]} icon={<Icon />} title={s.name} description={<span className="flex items-center gap-2"><SegmentStatusBadge status={s.status} /><Badge variant="outline">{t(`segmentType.${s.type}`)}</Badge>{s.description}</span>} />
      <div className="grid gap-6 xl:grid-cols-[340px_1fr]">
        <Card className="h-fit">
          <CardHeader>
            <CardTitle>{t('segment.info')}</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4 text-sm">
            <div className="rounded-xl bg-accent px-4 py-3">
              <div className="text-xs text-accent-foreground/80">{t('segment.count')}</div>
              <div className="flex items-center gap-2 text-[26px] font-semibold text-accent-foreground tabular">
                {s.status === 'BUILDING' && <Loader2 className="size-5 animate-spin" />}
                {s.status === 'BUILDING' && s.totalUsers === 0 ? '…' : fmtNumber(s.totalUsers)}
              </div>
              {s.status === 'BUILDING' && <div className="text-xs text-accent-foreground/80">{t('segment.loadingSoFar')}</div>}
            </div>
            {s.status === 'FAILED' && (
              <div className="grid gap-2 rounded-xl border border-danger/20 bg-danger-soft px-4 py-3 text-danger">
                <div className="flex items-start gap-2">
                  <CircleAlert className="mt-0.5 size-4 shrink-0" />
                  <span className="text-[13px]">{s.errorMessage || t('segment.buildFailed')}</span>
                </div>
                {RETRYABLE_SEGMENT_TYPES.includes(s.type) && can('SEGMENT_MANAGE') && (
                  <Button
                    variant="outline"
                    size="sm"
                    className="w-fit"
                    loading={retry.isPending}
                    onClick={() =>
                      retry.mutate(s.id, {
                        onSuccess: () => toast.success(t('segment.retryStarted')),
                        onError: (e) => toast.error(errorMessage(e)),
                      })
                    }
                  >
                    <RotateCcw />
                    {t('segment.retry')}
                  </Button>
                )}
              </div>
            )}
            {(
              [
                [t('segment.source'), t(`segmentSource.${s.source}`)],
                ...(s.audience ? [[t('segment.aud.table'), <span className="font-mono text-[13px]">{s.audience.tableName}</span>]] : []),
                ...(s.type === 'AUDIENCE' ? [[t('segment.aud.importedRows'), fmtNumber(s.importedRows ?? 0)]] : []),
                [t('segment.usedBy'), t('segment.usedByN', { n: s.referencedBy })],
                [t('common.createdAt'), `${fmtDateTime(s.createdAt)} · ${s.createdBy}`],
                ...(s.fileName ? [[t('segment.file'), s.fileName]] : []),
              ] as [string, React.ReactNode][]
            ).map(([k, v]) => (
              <div key={k}>
                <div className="text-xs text-muted-foreground">{k}</div>
                <div className="mt-0.5">{v}</div>
              </div>
            ))}
            {s.audience && (
              <div>
                <div className="mb-2 text-xs text-muted-foreground">{t('segment.aud.selectedColumns')}</div>
                <ul className="flex flex-wrap gap-1.5">
                  {s.audience.selectedColumns.map((c) => (
                    <li key={c.columnName}>
                      <Badge variant={c.targetField === PHONE_TARGET ? 'brand' : 'outline'} className="font-mono text-[11px]">
                        {c.columnName}
                        {c.targetField === PHONE_TARGET && ` · ${t('segment.aud.phoneTag')}`}
                      </Badge>
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {s.audience && (
              <div>
                <div className="mb-2 text-xs text-muted-foreground">{t('segment.conditions')}</div>
                {s.audience.conditions.length === 0 ? (
                  <p className="text-[13px] text-muted-foreground">{t('segment.aud.noConditions')}</p>
                ) : (
                  <ul className="grid gap-1.5">
                    {s.audience.conditions.map((c, i) => (
                      <li key={i} className="rounded-lg border bg-muted/40 px-3 py-2 font-mono text-xs">
                        {i > 0 && <span className="mr-1.5 font-semibold text-brand-600">{s.audience!.conditions[i - 1].logicalOperator ?? 'AND'}</span>}
                        {c.columnName} <span className="text-muted-foreground" title={t(`audienceOp.${c.operator}`)}>{OPERATOR_SYMBOL[c.operator]}</span> {c.value}
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            )}
            {s.conditions.length > 0 && (
              <div>
                <div className="mb-2 text-xs text-muted-foreground">{t('segment.conditions')}</div>
                <ul className="grid gap-1.5">
                  {s.conditions.map((c, i) => (
                    <li key={i} className="rounded-lg border bg-muted/40 px-3 py-2 font-mono text-xs">
                      {i > 0 && <span className="mr-1.5 font-semibold text-brand-600">{c.join}</span>}
                      {c.field} <span className="text-muted-foreground">{t(`op.${c.operator}`)}</span> {c.operator !== 'IS_NULL' && (c.operator === 'BETWEEN' ? `${c.value} – ${c.valueTo}` : c.value)}
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </CardContent>
        </Card>
        <div>
          <h3 className="mb-3 text-[15px] font-semibold">{t('segment.members')}</h3>
          {s.status !== 'READY' ? (
            <Card>
              <EmptyState icon={<Loader2 className="animate-spin" />} title={t('segment.notReady')} description={t('segment.notReadyDesc')} />
            </Card>
          ) : (
            <DataTable dense columns={columns} rows={members.data?.items} rowKey={(m) => m.recipientKey} loading={members.isLoading} error={members.isError ? errorMessage(members.error) : undefined} meta={members.data?.meta} onPageChange={setPage} />
          )}
        </div>
      </div>
    </>
  )
}

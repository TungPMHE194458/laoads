import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router-dom'
import { Loader2, UsersRound } from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { SegmentMember } from '@/api/types'
import { useSegment, useSegmentMembers } from '@/features/campaign/api'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { SegmentStatusBadge } from '@/components/common/status'
import { EmptyState } from '@/components/common/EmptyState'
import { Badge } from '@/components/ui/badge'
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
              <div className="text-[26px] font-semibold text-accent-foreground tabular">{s.status === 'BUILDING' ? <Loader2 className="my-1 size-6 animate-spin" /> : fmtNumber(s.totalUsers)}</div>
            </div>
            {[
              [t('segment.source'), s.source],
              [t('segment.usedBy'), t('segment.usedByN', { n: s.referencedBy })],
              [t('common.createdAt'), `${fmtDateTime(s.createdAt)} · ${s.createdBy}`],
              ...(s.fileName ? [[t('segment.file'), s.fileName]] : []),
            ].map(([k, v]) => (
              <div key={k}>
                <div className="text-xs text-muted-foreground">{k}</div>
                <div className="mt-0.5">{v}</div>
              </div>
            ))}
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

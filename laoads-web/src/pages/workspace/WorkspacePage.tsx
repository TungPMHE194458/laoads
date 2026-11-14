import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { toast } from 'sonner'
import { ArrowRight, Building2, Mail, Pencil, Phone, ShieldCheck, UserRound, Users } from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { WorkspaceUpdateInput } from '@/api/types'
import { useCan, useCurrentWorkspace } from '@/features/auth/permissions'
import { useUpdateWorkspace, useWorkspace } from '@/features/workspace/api'
import { useDashboardSummary } from '@/features/campaign/api'
import { PageHeader } from '@/components/common/PageHeader'
import { FormDialog, Field } from '@/components/common/dialogs'
import { StatCard } from '@/components/common/StatCard'
import { ActiveBadge } from '@/components/common/status'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input, Textarea } from '@/components/ui/input'
import { Skeleton } from '@/components/ui/misc'
import { fmtCompact, fmtDateTime } from '@/lib/format'

export default function WorkspacePage() {
  const { t } = useTranslation()
  const can = useCan()
  const cur = useCurrentWorkspace()
  const { data: ws, isLoading } = useWorkspace(cur?.id ?? null)
  const summary = useDashboardSummary(7)
  const update = useUpdateWorkspace(cur?.id ?? 0)
  const [open, setOpen] = React.useState(false)
  const [form, setForm] = React.useState<WorkspaceUpdateInput>({ name: '' })

  const startEdit = () => {
    if (!ws) return
    setForm({ name: ws.name, description: ws.description, contactName: ws.contactName, contactEmail: ws.contactEmail, contactPhone: ws.contactPhone })
    setOpen(true)
  }

  if (isLoading || !ws) return <Skeleton className="h-96 rounded-xl" />

  return (
    <>
      <PageHeader
        icon={<Building2 />}
        title={ws.name}
        description={
          <span className="flex items-center gap-2">
            <Badge variant="outline" className="font-mono">
              {ws.code}
            </Badge>
            <ActiveBadge status={ws.status} />
            {cur?.role && <Badge variant="brand">{t(`role.${cur.role}`)}</Badge>}
          </span>
        }
        actions={
          can('WORKSPACE_UPDATE') && (
            <Button variant="outline" onClick={startEdit}>
              <Pencil />
              {t('common.edit')}
            </Button>
          )
        }
      />

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label={t('dashboard.running')} metric={summary.data?.runningCampaigns} loading={summary.isLoading} />
        <StatCard label={t('dashboard.pending')} metric={summary.data?.pendingApprovals} loading={summary.isLoading} />
        <StatCard label={t('dashboard.reached')} metric={summary.data?.reachedCustomers} loading={summary.isLoading} format={fmtCompact} />
        <StatCard label={t('workspace.members')} value={ws.memberCount} icon={<Users />} hint={<Link to="/workspace/members" className="inline-flex items-center gap-1 text-link hover:underline">{t('workspace.manageMembers')} <ArrowRight className="size-3" /></Link>} />
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-[1fr_380px]">
        <Card>
          <CardHeader>
            <div>
              <CardTitle>{t('workspace.about')}</CardTitle>
              <CardDescription>{t('workspace.aboutDesc')}</CardDescription>
            </div>
          </CardHeader>
          <CardContent>
            <p className="text-sm leading-relaxed text-foreground/85">{ws.description || <span className="text-muted-foreground">{t('workspace.noDesc')}</span>}</p>
            <dl className="mt-6 grid gap-4 border-t pt-5 text-sm sm:grid-cols-3">
              {[
                [UserRound, t('workspace.contactName'), ws.contactName],
                [Mail, t('workspace.contactEmail'), ws.contactEmail],
                [Phone, t('workspace.contactPhone'), ws.contactPhone],
              ].map(([I, k, v]) => {
                const Icon = I as typeof Mail
                return (
                  <div key={k as string} className="flex gap-3">
                    <Icon className="mt-0.5 size-4 text-muted-foreground" />
                    <div className="min-w-0">
                      <dt className="text-xs text-muted-foreground">{k as string}</dt>
                      <dd className="mt-0.5 truncate">{(v as string) || t('common.notUpdated')}</dd>
                    </div>
                  </div>
                )
              })}
            </dl>
            <p className="mt-6 text-xs text-muted-foreground">
              {t('common.createdAt')} {fmtDateTime(ws.createdAt)}
              {ws.updatedAt && ` · ${t('common.updatedAt')} ${fmtDateTime(ws.updatedAt)}`}
            </p>
          </CardContent>
        </Card>
        <Card className="h-fit">
          <CardHeader>
            <div>
              <CardTitle className="flex items-center gap-2">
                <ShieldCheck className="size-4 text-brand-600" />
                {t('workspace.myPermissions')}
              </CardTitle>
              <CardDescription>{t('workspace.myPermissionsDesc')}</CardDescription>
            </div>
          </CardHeader>
          <CardContent className="flex flex-wrap gap-1.5">
            {cur?.permissions.map((p) => (
              <Badge key={p} variant="neutral">
                {t(`perm.${p}`)}
              </Badge>
            ))}
          </CardContent>
        </Card>
      </div>

      <FormDialog
        open={open}
        onOpenChange={setOpen}
        title={t('workspace.editTitle')}
        loading={update.isPending}
        onSubmit={() =>
          update.mutate(form, {
            onSuccess: () => {
              toast.success(t('common.saved'))
              setOpen(false)
            },
            onError: (e) => toast.error(errorMessage(e)),
          })
        }
      >
        <Field label={t('workspace.name')} required>
          <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
        </Field>
        <Field label={t('segment.description')}>
          <Textarea rows={3} value={form.description ?? ''} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label={t('workspace.contactName')}>
            <Input value={form.contactName ?? ''} onChange={(e) => setForm({ ...form, contactName: e.target.value })} />
          </Field>
          <Field label={t('workspace.contactPhone')}>
            <Input value={form.contactPhone ?? ''} onChange={(e) => setForm({ ...form, contactPhone: e.target.value })} />
          </Field>
        </div>
        <Field label={t('workspace.contactEmail')}>
          <Input type="email" value={form.contactEmail ?? ''} onChange={(e) => setForm({ ...form, contactEmail: e.target.value })} />
        </Field>
      </FormDialog>
    </>
  )
}

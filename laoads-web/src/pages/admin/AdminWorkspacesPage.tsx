import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Eye, Layers, Lock, LockOpen, Plus } from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { Workspace } from '@/api/types'
import { useAdminWorkspaces, useCreateWorkspace, useSetWorkspaceStatus } from '@/features/workspace/api'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { SearchInput } from '@/components/common/SearchInput'
import { ConfirmDialog, FormDialog, Field } from '@/components/common/dialogs'
import { ActiveBadge } from '@/components/common/status'
import { Button } from '@/components/ui/button'
import { Input, Textarea } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { fmtDate } from '@/lib/format'
import { WorkspaceDetailSheet } from './WorkspaceDetailSheet'

export default function AdminWorkspacesPage() {
  const { t } = useTranslation()
  const [page, setPage] = React.useState(0)
  const [query, setQuery] = React.useState('')
  const [status, setStatus] = React.useState('')
  const list = useAdminWorkspaces({ page, size: 10, query, status })
  const create = useCreateWorkspace()
  const setSt = useSetWorkspaceStatus()
  const [open, setOpen] = React.useState(false)
  const [form, setForm] = React.useState({ code: '', name: '', description: '', ownerUsername: '' })
  const [target, setTarget] = React.useState<Workspace | null>(null)
  const [viewing, setViewing] = React.useState<Workspace | null>(null)

  const columns: Column<Workspace>[] = [
    {
      key: 'name',
      header: t('workspace.name'),
      cell: (w) => (
        <div className="flex items-center gap-3">
          <span className="grid size-9 place-items-center rounded-lg bg-brand-gradient text-[11px] font-bold text-white">{w.code.slice(0, 3)}</span>
          <div>
            <div className="font-medium">{w.name}</div>
            <div className="font-mono text-xs text-muted-foreground">{w.code}</div>
          </div>
        </div>
      ),
    },
    { key: 'desc', header: t('segment.description'), cell: (w) => <span className="line-clamp-2 max-w-md text-[13px] text-muted-foreground">{w.description ?? '—'}</span> },
    { key: 'members', header: t('workspace.members'), align: 'right', cell: (w) => <span className="tabular">{w.memberCount}</span> },
    { key: 'status', header: t('common.status'), cell: (w) => <ActiveBadge status={w.status} /> },
    { key: 'created', header: t('common.createdAt'), cell: (w) => <span className="text-[13px] tabular">{fmtDate(w.createdAt)}</span> },
    {
      key: 'act',
      header: '',
      align: 'right',
      cell: (w) => (
        <div className="flex justify-end gap-1" onClick={(e) => e.stopPropagation()}>
          <Button size="sm" variant="ghost" onClick={() => setViewing(w)}>
            <Eye />
            {t('common.view')}
          </Button>
          <Button size="sm" variant="ghost" onClick={() => setTarget(w)}>
            {w.status === 'ACTIVE' ? <Lock /> : <LockOpen />}
            {w.status === 'ACTIVE' ? t('common.lock') : t('common.unlock')}
          </Button>
        </div>
      ),
    },
  ]

  return (
    <>
      <PageHeader
        icon={<Layers />}
        title={t('nav.adminWorkspaces')}
        description={t('adminWs.desc')}
        actions={
          <Button onClick={() => setOpen(true)}>
            <Plus />
            {t('adminWs.create')}
          </Button>
        }
      />
      <DataTable
        toolbar={
          <>
            <SearchInput value={query} onChange={(v) => { setQuery(v); setPage(0) }} placeholder={t('adminWs.searchPh')} />
            <Select className="ml-auto w-40" value={status} onValueChange={(v) => { setStatus(v); setPage(0) }} allLabel={t('common.allStatus')} options={(['ACTIVE', 'LOCKED'] as const).map((s) => ({ value: s, label: t(`activeStatus.${s}`) }))} />
          </>
        }
        columns={columns}
        rows={list.data?.items}
        rowKey={(w) => w.id}
        loading={list.isLoading}
        error={list.isError ? errorMessage(list.error) : undefined}
        meta={list.data?.meta}
        onPageChange={setPage}
      />
      <FormDialog
        open={open}
        onOpenChange={setOpen}
        title={t('adminWs.create')}
        description={t('adminWs.createDesc')}
        loading={create.isPending}
        onSubmit={() =>
          create.mutate(form, {
            onSuccess: () => {
              toast.success(t('adminWs.created'))
              setOpen(false)
              setForm({ code: '', name: '', description: '', ownerUsername: '' })
            },
            onError: (e) => toast.error(errorMessage(e)),
          })
        }
      >
        <div className="grid gap-4 sm:grid-cols-[140px_1fr]">
          <Field label={t('adminWs.code')} required>
            <Input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value.toUpperCase().replace(/[^A-Z0-9_]/g, '') })} maxLength={20} className="font-mono" required />
          </Field>
          <Field label={t('workspace.name')} required>
            <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          </Field>
        </div>
        <Field label={t('segment.description')}>
          <Textarea rows={2} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </Field>
        <Field label={t('adminWs.owner')} required hint={t('adminWs.ownerHint')}>
          <Input value={form.ownerUsername} onChange={(e) => setForm({ ...form, ownerUsername: e.target.value })} required />
        </Field>
      </FormDialog>
      <ConfirmDialog
        open={!!target}
        onOpenChange={(o) => !o && setTarget(null)}
        destructive={target?.status === 'ACTIVE'}
        title={target?.status === 'ACTIVE' ? t('adminWs.lockTitle') : t('adminWs.unlockTitle')}
        description={t(target?.status === 'ACTIVE' ? 'adminWs.lockDesc' : 'adminWs.unlockDesc', { name: target?.name })}
        loading={setSt.isPending}
        onConfirm={() =>
          target &&
          setSt.mutate(
            { id: target.id, status: target.status === 'ACTIVE' ? 'LOCKED' : 'ACTIVE' },
            {
              onSuccess: () => {
                toast.success(t('common.saved'))
                setTarget(null)
              },
              onError: (e) => toast.error(errorMessage(e)),
            },
          )
        }
      />
      <WorkspaceDetailSheet workspace={viewing} onClose={() => setViewing(null)} />
    </>
  )
}

import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { Eye, Pencil, Trash2, UserCog, UserPlus } from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { Member, WorkspaceRole } from '@/api/types'
import { useMe } from '@/features/auth/api'
import { useCan, useCurrentWorkspace, useIsSuperAdmin } from '@/features/auth/permissions'
import { useAddMember, useChangeMemberRole, useMembers, useRemoveMember } from '@/features/workspace/api'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { SearchInput } from '@/components/common/SearchInput'
import { ConfirmDialog, FormDialog, Field } from '@/components/common/dialogs'
import { ActiveBadge } from '@/components/common/status'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { Tooltip } from '@/components/ui/misc'
import { initials } from '@/lib/utils'
import { MemberDetailSheet } from './MemberDetailSheet'
import { fmtDate } from '@/lib/format'

const ROLES: WorkspaceRole[] = ['OWNER', 'ADMIN', 'MEMBER']

export default function MembersPage() {
  const { t } = useTranslation()
  const can = useCan()
  const isSa = useIsSuperAdmin()
  const { data: me } = useMe()
  const ws = useCurrentWorkspace()
  const wsId = ws?.id ?? 0
  const [page, setPage] = React.useState(0)
  const [size, setSize] = React.useState(10)
  const [query, setQuery] = React.useState('')
  const [role, setRole] = React.useState<WorkspaceRole | ''>('')
  // SRS 3.4.1: lọc trạng thái, mặc định "Tất cả"
  const [status, setStatus] = React.useState<'' | 'ACTIVE' | 'REVOKED'>('')
  const [detail, setDetail] = React.useState<Member | null>(null)
  const list = useMembers(ws?.id ?? null, { page, size, query, role, status })
  const add = useAddMember(wsId)
  const changeRole = useChangeMemberRole(wsId)
  const remove = useRemoveMember(wsId)
  const [addOpen, setAddOpen] = React.useState(false)
  const [addForm, setAddForm] = React.useState({ identifier: '', role: 'MEMBER' as WorkspaceRole })
  const [roleTarget, setRoleTarget] = React.useState<Member | null>(null)
  const [newRole, setNewRole] = React.useState<WorkspaceRole>('MEMBER')
  const [removeTarget, setRemoveTarget] = React.useState<Member | null>(null)

  const manage = can('MEMBER_MANAGE')
  const iAmOwner = ws?.role === 'OWNER' || isSa
  // SRS 3.4: chỉ Owner được gán vai trò Chủ sở hữu
  const assignable = ROLES.filter((r) => r !== 'OWNER' || iAmOwner)
  const onErr = (e: unknown) => toast.error(errorMessage(e))
  // Quy tắc SRS 3.4.3–3.4.4: không tự sửa / gỡ chính mình; chỉ Owner đổi vai trò Owner; không gỡ Owner
  const rules = (m: Member) => {
    const self = m.userId === me?.user.id
    const active = m.status === 'ACTIVE'
    return {
      self,
      canRole: active && manage && !self && (m.role !== 'OWNER' || iAmOwner),
      canRemove: active && manage && !self && m.role !== 'OWNER',
    }
  }
  const openEdit = (m: Member) => {
    setRoleTarget(m)
    setNewRole(m.role)
  }
  const iconBtn = (label: string, icon: React.ReactNode, onClick: () => void, enabled: boolean, danger = false) => (
    <Tooltip content={enabled ? label : t('members.notAllowed')}>
      {/* span bọc để tooltip vẫn hiện khi nút bị vô hiệu */}
      <span>
        <Button
          variant="ghost"
          size="icon-sm"
          aria-label={label}
          disabled={!enabled}
          className={danger ? 'text-muted-foreground hover:text-danger' : undefined}
          onClick={(e) => {
            e.stopPropagation()
            onClick()
          }}
        >
          {icon}
        </Button>
      </span>
    </Tooltip>
  )

  const columns: Column<Member>[] = [
    { key: 'id', header: 'ID', width: '64px', cell: (m) => <span className="text-muted-foreground tabular">{m.id}</span> },
    {
      key: 'user',
      header: t('members.member'),
      cell: (m) => (
        <div className="flex items-center gap-3">
          <span className="grid size-9 shrink-0 place-items-center rounded-full bg-muted text-xs font-semibold">{initials(m.fullName ?? m.username)}</span>
          <div className="min-w-0">
            <div className="flex items-center gap-2 font-medium">
              {m.fullName ?? m.username}
              {m.userId === me?.user.id && <Badge variant="info">{t('members.you')}</Badge>}
            </div>
            <div className="text-xs text-muted-foreground">@{m.username}</div>
          </div>
        </div>
      ),
    },
    { key: 'contact', header: t('members.contact'), cell: (m) => <div className="text-[13px]">{m.email ?? '—'}<div className="text-xs text-muted-foreground">{m.phone ?? ''}</div></div> },
    { key: 'role', header: t('members.role'), cell: (m) => <Badge variant={m.role === 'OWNER' ? 'brand' : m.role === 'ADMIN' ? 'info' : 'neutral'}>{t(`role.${m.role}`)}</Badge> },
    { key: 'status', header: t('common.status'), cell: (m) => <ActiveBadge status={m.status} /> },
    { key: 'joined', header: t('members.joined'), cell: (m) => <span className="text-[13px] tabular">{fmtDate(m.joinedAt)}</span> },
    {
      key: 'actions',
      header: '',
      width: '120px',
      align: 'right',
      cell: (m) => {
        const r = rules(m)
        return (
          <div className="flex justify-end gap-0.5" onClick={(e) => e.stopPropagation()}>
            {iconBtn(t('common.view'), <Eye />, () => setDetail(m), true)}
            {manage && iconBtn(t('members.editMember'), <Pencil />, () => openEdit(m), r.canRole)}
            {manage && iconBtn(t('members.remove'), <Trash2 />, () => setRemoveTarget(m), r.canRemove, true)}
          </div>
        )
      },
    },
  ]

  return (
    <>
      <PageHeader
        icon={<UserCog />}
        crumbs={[{ label: t('nav.workspaceInfo'), to: '/workspace' }, { label: t('nav.members') }]}
        title={t('nav.members')}
        description={t('members.desc', { ws: ws?.name ?? '' })}
        actions={
          manage && (
            <Button onClick={() => setAddOpen(true)}>
              <UserPlus />
              {t('members.add')}
            </Button>
          )
        }
      />
      <DataTable
        toolbar={
          <>
            <SearchInput value={query} onChange={(v) => { setQuery(v); setPage(0) }} placeholder={t('members.searchPh')} />
            <div className="ml-auto flex flex-wrap gap-2">
              <Select className="w-44" value={role} onValueChange={(v) => { setRole(v as WorkspaceRole | ''); setPage(0) }} allLabel={t('members.allRoles')} options={ROLES.map((r) => ({ value: r, label: t(`role.${r}`) }))} />
              <Select className="w-44" value={status} onValueChange={(v) => { setStatus(v as '' | 'ACTIVE' | 'REVOKED'); setPage(0) }} allLabel={t('common.allStatus')} options={(['ACTIVE', 'REVOKED'] as const).map((s) => ({ value: s, label: t(`memberStatus.${s}`) }))} />
            </div>
          </>
        }
        columns={columns}
        rows={list.data?.items}
        rowKey={(m) => m.id}
        onRowClick={setDetail}
        loading={list.isLoading}
        error={list.isError ? errorMessage(list.error) : undefined}
        meta={list.data?.meta}
        onPageChange={setPage}
        onPageSizeChange={(n) => {
          setSize(n)
          setPage(0)
        }}
      />

      <FormDialog
        open={addOpen}
        onOpenChange={setAddOpen}
        title={t('members.add')}
        description={t('members.addDesc')}
        submitLabel={t('members.addBtn')}
        loading={add.isPending}
        onSubmit={() =>
          add.mutate(addForm, {
            onSuccess: () => {
              toast.success(t('members.added'))
              setAddOpen(false)
              setAddForm({ identifier: '', role: 'MEMBER' })
            },
            onError: onErr,
          })
        }
      >
        <Field label={t('members.identifier')} required hint={t('members.identifierHint')}>
          <Input value={addForm.identifier} onChange={(e) => setAddForm({ ...addForm, identifier: e.target.value })} required autoFocus />
        </Field>
        <Field label={t('members.role')} required>
          <Select value={addForm.role} onValueChange={(v) => setAddForm({ ...addForm, role: v as WorkspaceRole })} options={assignable.map((r) => ({ value: r, label: t(`role.${r}`) }))} />
        </Field>
      </FormDialog>

      <FormDialog
        open={!!roleTarget}
        onOpenChange={(o) => !o && setRoleTarget(null)}
        title={t('members.editMember')}
        description={t('members.editDesc')}
        loading={changeRole.isPending}
        onSubmit={() =>
          roleTarget &&
          changeRole.mutate(
            { memberId: roleTarget.id, role: newRole },
            {
              onSuccess: () => {
                toast.success(t('common.saved'))
                setRoleTarget(null)
              },
              onError: onErr,
            },
          )
        }
      >
        {roleTarget && (
          <div className="grid gap-3 rounded-lg border bg-muted/40 p-3 text-sm">
            <div className="flex items-center justify-between gap-3">
              <span className="font-medium">{roleTarget.fullName ?? roleTarget.username}</span>
              <ActiveBadge status={roleTarget.status} />
            </div>
            <Field label={t('members.fullName')}>
              <Input value={roleTarget.fullName ?? ''} readOnly disabled />
            </Field>
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label={t('members.phone')}>
                <Input value={roleTarget.phone ?? ''} readOnly disabled />
              </Field>
              <Field label={t('members.email')}>
                <Input value={roleTarget.email ?? ''} readOnly disabled />
              </Field>
            </div>
          </div>
        )}
        <Field label={t('members.role')} required>
          <Select value={newRole} onValueChange={(v) => setNewRole(v as WorkspaceRole)} options={assignable.map((r) => ({ value: r, label: t(`role.${r}`) }))} />
        </Field>
      </FormDialog>

      <ConfirmDialog
        open={!!removeTarget}
        onOpenChange={(o) => !o && setRemoveTarget(null)}
        destructive
        title={t('members.removeTitle')}
        description={t('members.removeDesc', { name: removeTarget?.fullName ?? removeTarget?.username })}
        confirmLabel={t('members.remove')}
        loading={remove.isPending}
        onConfirm={() =>
          removeTarget &&
          remove.mutate(removeTarget.id, {
            onSuccess: () => {
              toast.success(t('members.removed'))
              setRemoveTarget(null)
            },
            onError: onErr,
          })
        }
      />
      <MemberDetailSheet
        member={detail}
        onClose={() => setDetail(null)}
        onEdit={detail && rules(detail).canRole ? () => { openEdit(detail); setDetail(null) } : undefined}
        onRemove={detail && rules(detail).canRemove ? () => { setRemoveTarget(detail); setDetail(null) } : undefined}
      />
    </>
  )
}

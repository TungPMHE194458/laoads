import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { KeyRound, Lock, LockOpen, MoreHorizontal, Pencil, Plus, Users } from 'lucide-react'
import { errorMessage } from '@/api/client'
import type { SystemRole, UserDto } from '@/api/types'
import { useMe } from '@/features/auth/api'
import { useAdminUsers, useResetPassword, useSaveUser, useSetUserStatus, type UserInput } from '@/features/admin/api'
import { PageHeader } from '@/components/common/PageHeader'
import { DataTable, type Column } from '@/components/common/DataTable'
import { SearchInput } from '@/components/common/SearchInput'
import { FormDialog, Field } from '@/components/common/dialogs'
import { ActiveBadge } from '@/components/common/status'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Select } from '@/components/ui/select'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { initials } from '@/lib/utils'
import { fmtDateTime, fmtRelative } from '@/lib/format'

const EMPTY: UserInput = { username: '', fullName: '', email: '', phone: '', password: '', systemRole: 'USER' }

export default function AdminUsersPage() {
  const { t } = useTranslation()
  const { data: me } = useMe()
  const [page, setPage] = React.useState(0)
  const [query, setQuery] = React.useState('')
  const [status, setStatus] = React.useState('')
  const [role, setRole] = React.useState('')
  const list = useAdminUsers({ page, size: 10, query, status, systemRole: role })
  const save = useSaveUser()
  const setSt = useSetUserStatus()
  const reset = useResetPassword()
  const [editing, setEditing] = React.useState<null | { id?: number; form: UserInput }>(null)
  const [pwFor, setPwFor] = React.useState<UserDto | null>(null)
  const [pw, setPw] = React.useState('')
  const onErr = (e: unknown) => toast.error(errorMessage(e))

  const columns: Column<UserDto>[] = [
    {
      key: 'user',
      header: t('adminUsers.user'),
      cell: (u) => (
        <div className="flex items-center gap-3">
          <span className="grid size-9 shrink-0 place-items-center rounded-full bg-muted text-xs font-semibold">{initials(u.fullName ?? u.username)}</span>
          <div>
            <div className="font-medium">{u.fullName}</div>
            <div className="text-xs text-muted-foreground">@{u.username}</div>
          </div>
        </div>
      ),
    },
    { key: 'contact', header: t('members.contact'), cell: (u) => <div className="text-[13px]">{u.email ?? '—'}<div className="text-xs text-muted-foreground">{u.phone}</div></div> },
    { key: 'role', header: t('adminUsers.systemRole'), cell: (u) => <Badge variant={u.systemRole === 'SUPER_ADMIN' ? 'brand' : 'neutral'}>{t(`role.${u.systemRole}`)}</Badge> },
    { key: 'status', header: t('common.status'), cell: (u) => <ActiveBadge status={u.status} /> },
    { key: 'login', header: t('adminUsers.lastLogin'), cell: (u) => <span className="text-[13px]" title={fmtDateTime(u.lastLoginAt)}>{u.lastLoginAt ? fmtRelative(u.lastLoginAt) : t('adminUsers.never')}</span> },
    {
      key: 'menu',
      header: '',
      width: '48px',
      cell: (u) => (
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <Button variant="ghost" size="icon-sm" aria-label={t('common.actions')}>
              <MoreHorizontal />
            </Button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end">
            <DropdownMenuItem onSelect={() => setEditing({ id: u.id, form: { fullName: u.fullName ?? '', email: u.email, phone: u.phone, systemRole: u.systemRole } })}>
              <Pencil />
              {t('common.edit')}
            </DropdownMenuItem>
            <DropdownMenuItem onSelect={() => setPwFor(u)}>
              <KeyRound />
              {t('adminUsers.resetPw')}
            </DropdownMenuItem>
            {u.id !== me?.user.id && (
              <>
                <DropdownMenuSeparator />
                <DropdownMenuItem
                  variant={u.status === 'ACTIVE' ? 'destructive' : 'default'}
                  onSelect={() => setSt.mutate({ id: u.id, status: u.status === 'ACTIVE' ? 'LOCKED' : 'ACTIVE' }, { onSuccess: () => toast.success(t('common.saved')), onError: onErr })}
                >
                  {u.status === 'ACTIVE' ? <Lock /> : <LockOpen />}
                  {u.status === 'ACTIVE' ? t('common.lock') : t('common.unlock')}
                </DropdownMenuItem>
              </>
            )}
          </DropdownMenuContent>
        </DropdownMenu>
      ),
    },
  ]

  const f = editing?.form
  const setF = (p: Partial<UserInput>) => setEditing((e) => e && { ...e, form: { ...e.form, ...p } })

  return (
    <>
      <PageHeader
        icon={<Users />}
        title={t('nav.adminUsers')}
        description={t('adminUsers.desc')}
        actions={
          <Button onClick={() => setEditing({ form: { ...EMPTY } })}>
            <Plus />
            {t('adminUsers.create')}
          </Button>
        }
      />
      <DataTable
        toolbar={
          <>
            <SearchInput value={query} onChange={(v) => { setQuery(v); setPage(0) }} placeholder={t('adminUsers.searchPh')} />
            <div className="ml-auto flex gap-2">
              <Select className="w-40" value={role} onValueChange={(v) => { setRole(v); setPage(0) }} allLabel={t('adminUsers.allRoles')} options={(['SUPER_ADMIN', 'USER'] as SystemRole[]).map((r) => ({ value: r, label: t(`role.${r}`) }))} />
              <Select className="w-40" value={status} onValueChange={(v) => { setStatus(v); setPage(0) }} allLabel={t('common.allStatus')} options={(['ACTIVE', 'LOCKED'] as const).map((s) => ({ value: s, label: t(`activeStatus.${s}`) }))} />
            </div>
          </>
        }
        columns={columns}
        rows={list.data?.items}
        rowKey={(u) => u.id}
        loading={list.isLoading}
        error={list.isError ? errorMessage(list.error) : undefined}
        meta={list.data?.meta}
        onPageChange={setPage}
      />

      <FormDialog
        open={!!editing}
        onOpenChange={(o) => !o && setEditing(null)}
        title={editing?.id ? t('adminUsers.edit') : t('adminUsers.create')}
        loading={save.isPending}
        onSubmit={() =>
          editing &&
          save.mutate(
            { id: editing.id, ...editing.form },
            {
              onSuccess: () => {
                toast.success(t('common.saved'))
                setEditing(null)
              },
              onError: onErr,
            },
          )
        }
      >
        {f && (
          <>
            {!editing?.id && (
              <Field label={t('login.username')} required>
                <Input value={f.username ?? ''} onChange={(e) => setF({ username: e.target.value.trim() })} required autoComplete="off" />
              </Field>
            )}
            <Field label={t('adminUsers.fullName')} required>
              <Input value={f.fullName} onChange={(e) => setF({ fullName: e.target.value })} required />
            </Field>
            <div className="grid gap-4 sm:grid-cols-2">
              <Field label="Email">
                <Input type="email" value={f.email ?? ''} onChange={(e) => setF({ email: e.target.value })} />
              </Field>
              <Field label={t('workspace.contactPhone')}>
                <Input value={f.phone ?? ''} onChange={(e) => setF({ phone: e.target.value })} />
              </Field>
            </div>
            <Field label={t('adminUsers.systemRole')} required>
              <Select value={f.systemRole} onValueChange={(v) => setF({ systemRole: v as SystemRole })} options={(['USER', 'SUPER_ADMIN'] as SystemRole[]).map((r) => ({ value: r, label: t(`role.${r}`) }))} />
            </Field>
            {!editing?.id && (
              <Field label={t('login.password')} required hint={t('profile.pwHint')}>
                <Input type="password" value={f.password ?? ''} onChange={(e) => setF({ password: e.target.value })} required minLength={8} autoComplete="new-password" />
              </Field>
            )}
          </>
        )}
      </FormDialog>

      <FormDialog
        open={!!pwFor}
        onOpenChange={(o) => {
          if (!o) {
            setPwFor(null)
            setPw('')
          }
        }}
        title={t('adminUsers.resetPw')}
        description={pwFor?.fullName ?? pwFor?.username}
        loading={reset.isPending}
        onSubmit={() =>
          pwFor &&
          reset.mutate(
            { id: pwFor.id, newPassword: pw },
            {
              onSuccess: () => {
                toast.success(t('adminUsers.pwReset'))
                setPwFor(null)
                setPw('')
              },
              onError: onErr,
            },
          )
        }
      >
        <Field label={t('profile.newPw')} required hint={t('profile.pwHint')}>
          <Input type="password" value={pw} onChange={(e) => setPw(e.target.value)} minLength={8} required autoComplete="new-password" />
        </Field>
      </FormDialog>
    </>
  )
}

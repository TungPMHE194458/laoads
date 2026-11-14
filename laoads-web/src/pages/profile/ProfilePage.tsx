import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { toast } from 'sonner'
import { KeyRound, Mail, Phone, UserRound } from 'lucide-react'
import { errorMessage } from '@/api/client'
import { useChangePassword, useMe } from '@/features/auth/api'
import { PageHeader } from '@/components/common/PageHeader'
import { Field } from '@/components/common/dialogs'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { initials } from '@/lib/utils'
import { fmtDateTime } from '@/lib/format'

export default function ProfilePage() {
  const { t } = useTranslation()
  const { data } = useMe()
  const change = useChangePassword()
  const [form, setForm] = React.useState({ oldPassword: '', newPassword: '', confirm: '' })
  const [err, setErr] = React.useState<string | null>(null)
  const u = data?.user

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    setErr(null)
    if (form.newPassword.length < 8) return setErr(t('profile.pwTooShort'))
    if (form.newPassword !== form.confirm) return setErr(t('profile.pwMismatch'))
    change.mutate(
      { oldPassword: form.oldPassword, newPassword: form.newPassword },
      {
        onSuccess: () => {
          toast.success(t('profile.pwChanged'))
          setForm({ oldPassword: '', newPassword: '', confirm: '' })
        },
        onError: (e2) => setErr(errorMessage(e2)),
      },
    )
  }

  return (
    <>
      <PageHeader title={t('profile.title')} description={t('profile.subtitle')} />
      <div className="grid gap-6 lg:grid-cols-[360px_1fr]">
        <Card>
          <CardContent className="flex flex-col items-center pt-8 text-center">
            <span className="grid size-20 place-items-center rounded-full bg-gradient-to-br from-brand-400 to-brand-700 text-2xl font-semibold text-white">{initials(u?.fullName ?? u?.username)}</span>
            <h2 className="mt-4 text-lg font-semibold">{u?.fullName}</h2>
            <p className="text-sm text-muted-foreground">@{u?.username}</p>
            <Badge variant={u?.systemRole === 'SUPER_ADMIN' ? 'brand' : 'neutral'} className="mt-3">
              {t(`role.${u?.systemRole === 'SUPER_ADMIN' ? 'SUPER_ADMIN' : 'USER'}`)}
            </Badge>
            <dl className="mt-6 grid w-full gap-3 border-t pt-5 text-left text-sm">
              {[
                [Mail, u?.email],
                [Phone, u?.phone],
                [UserRound, `${t('profile.lastLogin')}: ${fmtDateTime(u?.lastLoginAt)}`],
              ].map(([I, v], i) => {
                const Icon = I as typeof Mail
                return (
                  <div key={i} className="flex items-center gap-3 text-muted-foreground">
                    <Icon className="size-4 shrink-0" />
                    <span className="truncate text-foreground/90">{(v as string) || '—'}</span>
                  </div>
                )
              })}
            </dl>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <div>
              <CardTitle className="flex items-center gap-2">
                <KeyRound className="size-4 text-brand-600" />
                {t('profile.changePw')}
              </CardTitle>
              <CardDescription>{t('profile.changePwDesc')}</CardDescription>
            </div>
          </CardHeader>
          <CardContent>
            <form onSubmit={submit} className="grid max-w-md gap-4">
              <Field label={t('profile.oldPw')} required htmlFor="old">
                <Input id="old" type="password" autoComplete="current-password" value={form.oldPassword} onChange={(e) => setForm({ ...form, oldPassword: e.target.value })} />
              </Field>
              <Field label={t('profile.newPw')} required htmlFor="new" hint={t('profile.pwHint')}>
                <Input id="new" type="password" autoComplete="new-password" value={form.newPassword} onChange={(e) => setForm({ ...form, newPassword: e.target.value })} />
              </Field>
              <Field label={t('profile.confirmPw')} required htmlFor="confirm" error={err}>
                <Input id="confirm" type="password" autoComplete="new-password" value={form.confirm} onChange={(e) => setForm({ ...form, confirm: e.target.value })} />
              </Field>
              <div>
                <Button type="submit" loading={change.isPending} disabled={!form.oldPassword || !form.newPassword}>
                  {t('profile.changePw')}
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
    </>
  )
}

import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom'
import { Eye, EyeOff, Lock, ShieldCheck, Sparkles, Target, TrendingUp, User } from 'lucide-react'
import { errorMessage } from '@/api/client'
import { useLogin, useVsaConfig } from '@/features/auth/api'
import { useAuthStore } from '@/stores/auth'
import { LANGS, LANG_LABEL, type Lang } from '@/i18n'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Tooltip } from '@/components/ui/misc'
import { Brand } from '@/components/layout/Brand'
import { cn } from '@/lib/utils'

const DEMO = [
  { u: 'superadmin', p: 'superadmin', role: 'SUPER_ADMIN' },
  { u: 'owner_bsd', p: 'owner', role: 'OWNER' },
  { u: 'admin_bsd', p: 'admin', role: 'ADMIN' },
  { u: 'member_bsd', p: 'member', role: 'MEMBER' },
]

function LangSwitch() {
  const { i18n } = useTranslation()
  const cur = (i18n.resolvedLanguage ?? 'vi') as Lang
  return (
    <div className="flex rounded-full border bg-card p-0.5 text-xs shadow-xs">
      {LANGS.map((l) => (
        <button
          key={l}
          onClick={() => void i18n.changeLanguage(l)}
          title={LANG_LABEL[l].label}
          className={cn('cursor-pointer rounded-full px-3 py-1.5 font-medium transition', l === cur ? 'bg-foreground text-background' : 'text-muted-foreground hover:text-foreground')}
        >
          {l.toUpperCase()}
        </button>
      ))}
    </div>
  )
}

function BrandPanel() {
  const { t } = useTranslation()
  const features = [
    { icon: Target, title: t('login.f1'), desc: t('login.f1d') },
    { icon: Sparkles, title: t('login.f2'), desc: t('login.f2d') },
    { icon: TrendingUp, title: t('login.f3'), desc: t('login.f3d') },
  ]
  return (
    <div className="relative hidden overflow-hidden bg-brand-gradient p-12 text-white lg:flex lg:flex-col">
      {/* hoạ tiết nền */}
      <div className="pointer-events-none absolute -top-32 -right-24 size-[28rem] rounded-full bg-white/10 blur-2xl" />
      <div className="pointer-events-none absolute -bottom-40 -left-20 size-[26rem] rounded-full bg-black/10 blur-2xl" />
      <svg className="pointer-events-none absolute inset-0 h-full w-full opacity-[0.08]" aria-hidden>
        <defs>
          <pattern id="dots" width="22" height="22" patternUnits="userSpaceOnUse">
            <circle cx="2" cy="2" r="1.4" fill="#fff" />
          </pattern>
        </defs>
        <rect width="100%" height="100%" fill="url(#dots)" />
      </svg>

      <Brand inverted className="relative" />
      <div className="relative mt-auto max-w-lg">
        <span className="inline-flex items-center gap-2 rounded-full bg-white/15 px-3 py-1 text-xs font-medium backdrop-blur">
          <span className="size-1.5 rounded-full bg-white" /> Unitel Campaign Platform
        </span>
        <h1 className="mt-5 text-[40px] leading-[1.1] font-bold tracking-tight">{t('login.headline')}</h1>
        <p className="mt-4 text-[15px] leading-relaxed text-white/85">{t('login.subhead')}</p>
        <ul className="mt-10 grid gap-5">
          {features.map((f) => (
            <li key={f.title} className="flex gap-4">
              <span className="grid size-11 shrink-0 place-items-center rounded-xl bg-white/15 ring-1 ring-white/25 backdrop-blur">
                <f.icon className="size-5" />
              </span>
              <span>
                <span className="block font-semibold">{f.title}</span>
                <span className="mt-0.5 block text-sm text-white/75">{f.desc}</span>
              </span>
            </li>
          ))}
        </ul>
      </div>
      <p className="relative mt-12 text-xs text-white/60">© {new Date().getFullYear()} Star Telecom Co., Ltd (Unitel)</p>
    </div>
  )
}

export default function LoginPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const token = useAuthStore((s) => s.accessToken)
  const login = useLogin()
  const vsa = useVsaConfig()
  const [username, setUsername] = React.useState('')
  const [password, setPassword] = React.useState('')
  const [show, setShow] = React.useState(false)
  const redirect = params.get('redirect') || '/dashboard'

  if (token && !login.isPending) return <Navigate to={redirect} replace />

  const submit = (e: React.FormEvent) => {
    e.preventDefault()
    if (!username.trim() || !password) return
    login.mutate({ username: username.trim(), password }, { onSuccess: () => navigate(redirect, { replace: true }) })
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-[minmax(0,1.05fr)_minmax(0,1fr)]">
      <BrandPanel />
      <div className="relative flex flex-col bg-background px-6 py-8 sm:px-12">
        <div className="flex items-center justify-between">
          <div className="lg:invisible">
            <Brand />
          </div>
          <LangSwitch />
        </div>
        <div className="mx-auto flex w-full max-w-[400px] flex-1 flex-col justify-center py-12">
          <h2 className="text-[28px] font-semibold tracking-tight">{t('login.title')}</h2>
          <p className="mt-2 text-sm text-muted-foreground">{t('login.subtitle')}</p>

          <form onSubmit={submit} className="mt-8 grid gap-4">
            <div className="grid gap-1.5">
              <Label htmlFor="username">{t('login.username')}</Label>
              <div className="relative">
                <User className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input id="username" autoComplete="username" autoFocus className="h-11 pl-9" value={username} onChange={(e) => setUsername(e.target.value)} placeholder={t('login.usernamePh')} />
              </div>
            </div>
            <div className="grid gap-1.5">
              <Label htmlFor="password">{t('login.password')}</Label>
              <div className="relative">
                <Lock className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
                <Input id="password" type={show ? 'text' : 'password'} autoComplete="current-password" className="h-11 pr-10 pl-9" value={password} onChange={(e) => setPassword(e.target.value)} placeholder="••••••••" />
                <button type="button" onClick={() => setShow((s) => !s)} aria-label={t('login.togglePw')} className="absolute top-1/2 right-2 grid size-7 -translate-y-1/2 cursor-pointer place-items-center rounded-md text-muted-foreground hover:bg-muted">
                  {show ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
                </button>
              </div>
            </div>
            {login.isError && (
              <p role="alert" className="rounded-lg border border-danger/20 bg-danger-soft px-3 py-2.5 text-sm text-danger">
                {errorMessage(login.error)}
              </p>
            )}
            <Button type="submit" size="lg" className="mt-2 w-full" loading={login.isPending} disabled={!username.trim() || !password}>
              {t('login.submit')}
            </Button>
          </form>

          <div className="my-6 flex items-center gap-3 text-xs text-muted-foreground">
            <span className="h-px flex-1 bg-border" />
            {t('login.or')}
            <span className="h-px flex-1 bg-border" />
          </div>
          <Tooltip content={vsa.data?.enabled ? null : t('login.vsaNotConfigured')}>
            <span>
              <Button
                variant="outline"
                size="lg"
                className="w-full"
                disabled={!vsa.data?.enabled || !vsa.data.loginUrl}
                onClick={() => vsa.data?.loginUrl && location.assign(vsa.data.loginUrl)}
              >
                <ShieldCheck className="text-brand-600" />
                {t('login.vsa')}
              </Button>
            </span>
          </Tooltip>

          {import.meta.env.DEV && (
            <div className="mt-8 rounded-xl border border-dashed p-4">
              <p className="text-xs font-medium text-muted-foreground">{t('login.demo')}</p>
              <div className="mt-2.5 flex flex-wrap gap-1.5">
                {DEMO.map((d) => (
                  <button
                    key={d.u}
                    type="button"
                    onClick={() => {
                      setUsername(d.u)
                      setPassword(d.p)
                    }}
                    className="cursor-pointer rounded-lg border bg-card px-2.5 py-1.5 text-left text-xs transition hover:border-brand-300 hover:bg-accent"
                  >
                    <span className="block font-semibold">{d.u}</span>
                    <span className="text-muted-foreground">{t(`role.${d.role}`)}</span>
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

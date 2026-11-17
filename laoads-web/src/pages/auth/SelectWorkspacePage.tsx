import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { ArrowRight, Building2, Lock, LogOut, Settings2 } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import { useMe, logout } from '@/features/auth/api'
import { useIsSuperAdmin } from '@/features/auth/permissions'
import { useUiStore } from '@/stores/ui'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Brand } from '@/components/layout/Brand'
import { EmptyState } from '@/components/common/EmptyState'
import { cn } from '@/lib/utils'

export default function SelectWorkspacePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const qc = useQueryClient()
  const { data } = useMe()
  const setWs = useUiStore((s) => s.setWorkspaceId)
  const current = useUiStore((s) => s.workspaceId)
  const isSa = useIsSuperAdmin()

  const choose = (id: number) => {
    setWs(id)
    qc.removeQueries({ predicate: (q) => q.queryKey[0] !== 'me' && q.queryKey[0] !== 'auth' })
    navigate('/dashboard', { replace: true })
  }

  return (
    <div className="min-h-screen bg-[radial-gradient(60rem_30rem_at_top,var(--brand-50),transparent)] dark:bg-none">
      <header className="flex h-16 items-center justify-between px-6">
        <Brand />
        <div className="flex items-center gap-1">
          {isSa && (
            <Button variant="outline" size="sm" onClick={() => navigate('/admin/workspaces')}>
              <Settings2 />
              {t('selectWs.systemAdmin')}
            </Button>
          )}
          <Button
          variant="ghost"
          size="sm"
          onClick={async () => {
            await logout()
            navigate('/login', { replace: true })
          }}
        >
          <LogOut />
          {t('layout.logout')}
          </Button>
        </div>
      </header>
      <main className="mx-auto max-w-3xl px-6 pt-10 pb-20">
        <h1 className="text-[28px] font-semibold tracking-tight">{t('selectWs.title', { name: data?.user.fullName ?? data?.user.username ?? '' })}</h1>
        <p className="mt-2 text-muted-foreground">{t('selectWs.subtitle')}</p>

        {data && data.workspaces.length === 0 ? (
          <div className="mt-10 rounded-2xl border bg-card">
            {isSa ? (
              <EmptyState
                icon={<Building2 />}
                title={t('selectWs.emptyTitleAdmin')}
                description={t('selectWs.emptyDescAdmin')}
                action={
                  <Button onClick={() => navigate('/admin/workspaces')}>
                    <Settings2 />
                    {t('nav.adminWorkspaces')}
                  </Button>
                }
              />
            ) : (
              <EmptyState icon={<Building2 />} title={t('selectWs.emptyTitle')} description={t('selectWs.emptyDesc')} />
            )}
          </div>
        ) : (
          <ul className="mt-8 grid gap-3 sm:grid-cols-2">
            {data?.workspaces.map((w) => {
              const locked = w.status !== 'ACTIVE'
              return (
                <li key={w.id}>
                  <button
                    disabled={locked}
                    onClick={() => choose(w.id)}
                    className={cn(
                      'group flex w-full cursor-pointer items-center gap-4 rounded-2xl border bg-card p-5 text-left shadow-card transition outline-none hover:-translate-y-0.5 hover:border-brand-300 hover:shadow-pop focus-visible:ring-[3px] focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:translate-y-0',
                      w.id === current && 'border-brand-400',
                    )}
                  >
                    <span className="grid size-12 shrink-0 place-items-center rounded-xl bg-brand-gradient text-sm font-bold text-white shadow-sm">{w.code.slice(0, 3)}</span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-semibold">{w.name}</span>
                      <span className="mt-1 flex items-center gap-2 text-xs text-muted-foreground">
                        <Badge variant={w.role === 'OWNER' ? 'brand' : 'neutral'}>{w.role ? t(`role.${w.role}`) : t('role.SUPER_ADMIN')}</Badge>
                        {t('selectWs.permCount', { n: w.permissions.length })}
                      </span>
                    </span>
                    {locked ? <Lock className="size-4 text-muted-foreground" /> : <ArrowRight className="size-4 text-muted-foreground transition group-hover:translate-x-0.5 group-hover:text-brand-600" />}
                  </button>
                </li>
              )
            })}
          </ul>
        )}
      </main>
    </div>
  )
}

import * as React from 'react'
import { useTranslation } from 'react-i18next'
import { Link, useNavigate } from 'react-router-dom'
import { Bell, Check, KeyRound, Languages, LogOut, Menu, Monitor, Moon, Plus, Sun } from 'lucide-react'
import { useMe, logout } from '@/features/auth/api'
import { useCan, useCurrentWorkspace } from '@/features/auth/permissions'
import { useApprovals } from '@/features/campaign/api'
import { LANGS, LANG_LABEL, type Lang } from '@/i18n'
import { useUiStore } from '@/stores/ui'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuCheckItem,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Dialog, SheetContent } from '@/components/ui/dialog'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/misc'
import { initials } from '@/lib/utils'
import { fmtRelative } from '@/lib/format'
import { Brand } from './Brand'
import { SidebarNav } from './Sidebar'
import { WorkspaceSwitcher } from './WorkspaceSwitcher'

function LanguageMenu() {
  const { i18n, t } = useTranslation()
  const cur = (i18n.resolvedLanguage ?? 'vi') as Lang
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="sm" className="gap-1.5 px-2.5" aria-label={t('layout.language')}>
          <Languages className="text-muted-foreground" />
          <span className="hidden text-[13px] sm:inline">{cur.toUpperCase()}</span>
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {LANGS.map((l) => (
          <DropdownMenuCheckItem key={l} checked={l === cur} onSelect={() => void i18n.changeLanguage(l)}>
            <span className="text-base leading-none">{LANG_LABEL[l].flag}</span>
            {LANG_LABEL[l].label}
          </DropdownMenuCheckItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

function ThemeMenu() {
  const { t } = useTranslation()
  const theme = useUiStore((s) => s.theme)
  const setTheme = useUiStore((s) => s.setTheme)
  const Icon = theme === 'dark' ? Moon : theme === 'light' ? Sun : Monitor
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon" aria-label={t('layout.theme')}>
          <Icon className="text-muted-foreground" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        {(
          [
            ['light', Sun],
            ['dark', Moon],
            ['system', Monitor],
          ] as const
        ).map(([k, I]) => (
          <DropdownMenuCheckItem key={k} checked={theme === k} onSelect={() => setTheme(k)}>
            <I />
            {t(`layout.themes.${k}`)}
          </DropdownMenuCheckItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

function Notifications() {
  const { t } = useTranslation()
  const can = useCan()
  const enabled = can('CAMPAIGN_APPROVE')
  const { data } = useApprovals({ page: 0, size: 5 }, enabled)
  const items = enabled ? (data?.items ?? []) : []
  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button variant="ghost" size="icon" className="relative" aria-label={t('layout.notifications')}>
          <Bell className="text-muted-foreground" />
          {items.length > 0 && <span className="absolute top-2 right-2 size-2 rounded-full bg-brand-500 ring-2 ring-card" />}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[22rem] p-0">
        <div className="flex items-center justify-between border-b px-4 py-3">
          <span className="text-sm font-semibold">{t('layout.notifications')}</span>
          {items.length > 0 && <span className="text-xs text-muted-foreground">{t('layout.pendingCount', { n: data?.meta.totalItems ?? 0 })}</span>}
        </div>
        {items.length === 0 ? (
          <div className="flex flex-col items-center gap-2 px-4 py-10 text-sm text-muted-foreground">
            <Check className="size-5" />
            {t('layout.noNotifications')}
          </div>
        ) : (
          <ul className="max-h-80 overflow-y-auto">
            {items.map((c) => (
              <li key={c.id}>
                <Link to={`/approvals?focus=${c.id}`} className="flex gap-3 border-b px-4 py-3 transition last:border-0 hover:bg-muted/60">
                  <span className="mt-1 size-2 shrink-0 rounded-full bg-warning" />
                  <span className="min-w-0">
                    <span className="block truncate text-sm font-medium">{c.name}</span>
                    <span className="block text-xs text-muted-foreground">
                      {t('layout.waitingApproval')} · {c.createdBy} · {fmtRelative(c.createdAt)}
                    </span>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </PopoverContent>
    </Popover>
  )
}

function UserMenu() {
  const { t } = useTranslation()
  const { data } = useMe()
  const ws = useCurrentWorkspace()
  const navigate = useNavigate()
  const u = data?.user
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <button className="flex cursor-pointer items-center gap-2.5 rounded-full p-0.5 pr-1 outline-none focus-visible:ring-[3px] focus-visible:ring-ring md:rounded-xl md:pr-2.5 md:hover:bg-muted">
          <span className="grid size-8 place-items-center rounded-full bg-gradient-to-br from-brand-400 to-brand-700 text-xs font-semibold text-white">{initials(u?.fullName ?? u?.username)}</span>
          <span className="hidden text-left leading-tight md:block">
            <span className="block max-w-36 truncate text-[13px] font-semibold">{u?.fullName ?? u?.username}</span>
            <span className="block text-[11px] text-muted-foreground">
              {u?.systemRole === 'SUPER_ADMIN' ? t('role.SUPER_ADMIN') : ws?.role ? t(`role.${ws.role}`) : ''}
            </span>
          </span>
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-60">
        <DropdownMenuLabel className="font-normal">
          <div className="text-sm font-semibold text-foreground">{u?.fullName}</div>
          <div className="truncate text-xs">{u?.email ?? u?.username}</div>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => navigate('/profile')}>
          <KeyRound />
          {t('layout.profile')}
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem
          variant="destructive"
          onSelect={async () => {
            await logout()
            navigate('/login', { replace: true })
          }}
        >
          <LogOut />
          {t('layout.logout')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

export function Topbar() {
  const { t } = useTranslation()
  const can = useCan()
  const [mobileOpen, setMobileOpen] = React.useState(false)
  return (
    <header className="sticky top-0 z-30 flex h-16 items-center gap-2 border-b bg-background/80 px-4 backdrop-blur-md md:px-6">
      <Button variant="ghost" size="icon" className="lg:hidden" onClick={() => setMobileOpen(true)} aria-label={t('layout.menu')}>
        <Menu />
      </Button>
      <div className="lg:hidden">
        <Brand />
      </div>
      <div className="ml-auto flex items-center gap-1">
        {can('CAMPAIGN_CREATE') && (
          <Button asChild size="sm" className="mr-2 hidden sm:inline-flex">
            <Link to="/campaigns/new">
              <Plus />
              {t('campaign.create')}
            </Link>
          </Button>
        )}
        <LanguageMenu />
        <ThemeMenu />
        <Notifications />
        <div className="mx-1.5 h-6 w-px bg-border" />
        <UserMenu />
      </div>

      <Dialog open={mobileOpen} onOpenChange={setMobileOpen}>
        <SheetContent className="right-auto left-0 max-w-[290px] border-r border-l-0 data-[state=closed]:slide-out-to-left data-[state=open]:slide-in-from-left">
          <div className="flex h-16 items-center px-5">
            <Brand />
          </div>
          <div className="px-4 pb-4">
            <WorkspaceSwitcher />
          </div>
          <div className="flex-1 overflow-y-auto px-4 pb-6">
            <SidebarNav onNavigate={() => setMobileOpen(false)} />
          </div>
        </SheetContent>
      </Dialog>
    </header>
  )
}

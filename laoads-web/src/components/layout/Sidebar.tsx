import { useTranslation } from 'react-i18next'
import { NavLink } from 'react-router-dom'
import { PanelLeftClose, PanelLeftOpen } from 'lucide-react'
import { useApprovals } from '@/features/campaign/api'
import { useCan, useCurrentWorkspace, useIsSuperAdmin } from '@/features/auth/permissions'
import { useUiStore } from '@/stores/ui'
import { Tooltip } from '@/components/ui/misc'
import { cn } from '@/lib/utils'
import { Brand } from './Brand'
import { NAV, type NavItem } from './nav'
import { WorkspaceSwitcher } from './WorkspaceSwitcher'

function useVisible() {
  const can = useCan()
  const isSa = useIsSuperAdmin()
  const ws = useCurrentWorkspace()
  // Mục theo quyền workspace chỉ hiện khi đã chọn workspace (Super Admin chưa có workspace vẫn vào trang quản trị)
  return (i: NavItem) => (i.superAdmin ? isSa : i.perm ? !!ws && can(i.perm) : true)
}

function ApprovalCount() {
  const { data } = useApprovals({ page: 0, size: 1 })
  const n = data?.meta.totalItems ?? 0
  if (!n) return null
  return <span className="ml-auto rounded-full bg-brand-500 px-1.5 py-px text-[11px] font-semibold text-white tabular">{n}</span>
}

export function SidebarNav({ collapsed, onNavigate }: { collapsed?: boolean; onNavigate?: () => void }) {
  const { t } = useTranslation()
  const visible = useVisible()
  const can = useCan()
  return (
    <nav className="flex flex-col gap-5">
      {NAV.map((g) => {
        const items = g.items.filter(visible)
        if (!items.length) return null
        return (
          <div key={g.key}>
            {!collapsed && <div className="mb-1.5 px-3 text-[11px] font-semibold tracking-[0.08em] text-muted-foreground/80 uppercase">{t(`nav.group.${g.key}`)}</div>}
            <ul className="flex flex-col gap-0.5">
              {items.map((i) => {
                const link = (
                  <NavLink
                    to={i.to}
                    end={i.to === '/workspace'}
                    onClick={onNavigate}
                    className={({ isActive }) =>
                      cn(
                        'group relative flex h-9 items-center gap-3 rounded-lg px-3 text-sm font-medium text-sidebar-foreground transition outline-none hover:bg-muted hover:text-foreground focus-visible:ring-[3px] focus-visible:ring-ring',
                        collapsed && 'justify-center px-0',
                        isActive && 'bg-sidebar-active text-sidebar-active-foreground hover:bg-sidebar-active hover:text-sidebar-active-foreground',
                      )
                    }
                  >
                    {({ isActive }) => (
                      <>
                        {isActive && <span className="absolute top-1.5 bottom-1.5 -left-3 w-[3px] rounded-r-full bg-brand-500" aria-hidden />}
                        <i.icon className={cn('size-[18px] shrink-0', isActive ? 'text-brand-600 dark:text-brand-400' : 'text-muted-foreground group-hover:text-foreground')} />
                        {!collapsed && <span className="truncate">{t(`nav.${i.key}`)}</span>}
                        {!collapsed && i.badge === 'approvals' && can('CAMPAIGN_APPROVE') && <ApprovalCount />}
                      </>
                    )}
                  </NavLink>
                )
                return (
                  <li key={i.key}>
                    {collapsed ? (
                      <Tooltip content={t(`nav.${i.key}`)} side="right">
                        {link}
                      </Tooltip>
                    ) : (
                      link
                    )}
                  </li>
                )
              })}
            </ul>
          </div>
        )
      })}
    </nav>
  )
}

export function Sidebar() {
  const { t } = useTranslation()
  const collapsed = useUiStore((s) => s.sidebarCollapsed)
  const toggle = useUiStore((s) => s.toggleSidebar)
  return (
    <aside
      className={cn(
        'sticky top-0 hidden h-screen shrink-0 flex-col border-r border-sidebar-border bg-sidebar transition-[width] duration-200 lg:flex',
        collapsed ? 'w-[76px]' : 'w-[264px]',
      )}
    >
      <div className={cn('flex h-16 items-center', collapsed ? 'justify-center px-2' : 'px-5')}>
        <Brand collapsed={collapsed} />
      </div>
      <div className={cn('pb-4', collapsed ? 'px-3' : 'px-4')}>
        <WorkspaceSwitcher collapsed={collapsed} />
      </div>
      <div className={cn('flex-1 overflow-y-auto pb-4 scrollbar-thin', collapsed ? 'px-3' : 'px-4')}>
        <SidebarNav collapsed={collapsed} />
      </div>
      <div className={cn('border-t border-sidebar-border p-3', collapsed && 'flex justify-center')}>
        <button
          onClick={toggle}
          className="flex h-9 w-full cursor-pointer items-center gap-3 rounded-lg px-3 text-sm text-muted-foreground transition hover:bg-muted hover:text-foreground"
          aria-label={collapsed ? t('layout.expand') : t('layout.collapse')}
        >
          {collapsed ? <PanelLeftOpen className="size-[18px]" /> : <PanelLeftClose className="size-[18px]" />}
          {!collapsed && t('layout.collapse')}
        </button>
      </div>
    </aside>
  )
}

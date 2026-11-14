import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router-dom'
import { Check, ChevronsUpDown, Layers } from 'lucide-react'
import { useQueryClient } from '@tanstack/react-query'
import { useMe } from '@/features/auth/api'
import { useCurrentWorkspace } from '@/features/auth/permissions'
import { useUiStore } from '@/stores/ui'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'
import { Tooltip } from '@/components/ui/misc'
import { cn } from '@/lib/utils'

export function WorkspaceSwitcher({ collapsed }: { collapsed?: boolean }) {
  const { t } = useTranslation()
  const { data } = useMe()
  const current = useCurrentWorkspace()
  const setWs = useUiStore((s) => s.setWorkspaceId)
  const qc = useQueryClient()
  const navigate = useNavigate()

  const switchTo = (id: number) => {
    if (id === current?.id) return
    setWs(id)
    // Dữ liệu theo workspace phải tải lại với X-Workspace-Id mới
    qc.removeQueries({ predicate: (q) => q.queryKey[0] !== 'me' && q.queryKey[0] !== 'auth' })
    navigate('/dashboard')
  }

  const trigger = (
    <button
      className={cn(
        'flex w-full cursor-pointer items-center gap-2.5 rounded-xl border bg-card p-2 text-left shadow-xs transition hover:border-brand-300 outline-none focus-visible:ring-[3px] focus-visible:ring-ring',
        collapsed && 'justify-center p-1.5',
      )}
    >
      <span className="grid size-8 shrink-0 place-items-center rounded-lg bg-brand-gradient text-[11px] font-bold text-white">
        {current?.code.slice(0, 3) ?? <Layers className="size-4" />}
      </span>
      {!collapsed && (
        <>
          <span className="min-w-0 flex-1">
            <span className="block text-[11px] font-medium text-muted-foreground">{t('layout.workspace')}</span>
            <span className="block truncate text-sm font-semibold">{current?.name ?? t('layout.chooseWorkspace')}</span>
          </span>
          <ChevronsUpDown className="size-4 shrink-0 text-muted-foreground" />
        </>
      )}
    </button>
  )

  return (
    <DropdownMenu>
      {collapsed ? (
        <Tooltip content={current?.name} side="right">
          <DropdownMenuTrigger asChild>{trigger}</DropdownMenuTrigger>
        </Tooltip>
      ) : (
        <DropdownMenuTrigger asChild>{trigger}</DropdownMenuTrigger>
      )}
      <DropdownMenuContent align="start" className="w-64">
        <DropdownMenuLabel>{t('layout.switchWorkspace')}</DropdownMenuLabel>
        {data?.workspaces.map((w) => (
          <DropdownMenuItem key={w.id} onSelect={() => switchTo(w.id)} disabled={w.status !== 'ACTIVE'}>
            <span className="grid size-7 place-items-center rounded-md bg-muted text-[10px] font-bold text-foreground/80">{w.code.slice(0, 3)}</span>
            <span className="min-w-0 flex-1">
              <span className="block truncate font-medium">{w.name}</span>
              <span className="block text-xs text-muted-foreground">{w.role ? t(`role.${w.role}`) : t('role.SUPER_ADMIN')}</span>
            </span>
            {w.id === current?.id && <Check className="size-4 text-brand-600!" />}
          </DropdownMenuItem>
        ))}
        <DropdownMenuSeparator />
        <DropdownMenuItem onSelect={() => navigate('/select-workspace')}>
          <Layers />
          {t('layout.allWorkspaces')}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

import * as React from 'react'
import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { Loader2 } from 'lucide-react'
import type { Permission } from '@/api/types'
import { useMe } from '@/features/auth/api'
import { useCan, useIsSuperAdmin } from '@/features/auth/permissions'
import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

export function FullScreenLoader() {
  return (
    <div className="grid min-h-screen place-items-center">
      <Loader2 className="size-6 animate-spin text-brand-500" />
    </div>
  )
}

/** Cần đăng nhập + /me đã tải. Không có workspace hợp lệ → /select-workspace. */
export function RequireAuth({ needWorkspace = true }: { needWorkspace?: boolean }) {
  const token = useAuthStore((s) => s.accessToken)
  const loc = useLocation()
  const { data, isLoading, isError } = useMe()
  const wsId = useUiStore((s) => s.workspaceId)
  const setWs = useUiStore((s) => s.setWorkspaceId)

  const valid = data?.workspaces.some((w) => w.id === wsId && w.status === 'ACTIVE')
  // Chỉ có 1 workspace → tự chọn
  React.useEffect(() => {
    if (data && !valid) {
      const active = data.workspaces.filter((w) => w.status === 'ACTIVE')
      if (active.length === 1) setWs(active[0].id)
    }
  }, [data, valid, setWs])

  if (!token) return <Navigate to={`/login?redirect=${encodeURIComponent(loc.pathname + loc.search)}`} replace />
  if (isLoading) return <FullScreenLoader />
  if (isError || !data) return <Navigate to="/login" replace />
  if (needWorkspace && !valid) {
    const active = data.workspaces.filter((w) => w.status === 'ACTIVE')
    if (active.length === 1) return <FullScreenLoader />
    return <Navigate to="/select-workspace" replace />
  }
  return <Outlet />
}

/** Chặn route theo quyền (UX — BE vẫn kiểm tra thật). */
export function RequirePermission({ perm, superAdmin, children }: { perm?: Permission; superAdmin?: boolean; children: React.ReactNode }) {
  const can = useCan()
  const isSa = useIsSuperAdmin()
  const ok = superAdmin ? isSa : perm ? can(perm) : true
  if (!ok) return <Navigate to="/403" replace />
  return <>{children}</>
}

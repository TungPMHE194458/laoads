import * as React from 'react'
import type { MyWorkspace, Permission } from '@/api/types'
import { useUiStore } from '@/stores/ui'
import { useMe } from './api'

/** Workspace hiện tại (theo X-Workspace-Id đã chọn) kèm role + permissions của user trong đó. */
export function useCurrentWorkspace(): MyWorkspace | null {
  const { data } = useMe()
  const wsId = useUiStore((s) => s.workspaceId)
  return React.useMemo(() => data?.workspaces.find((w) => w.id === wsId) ?? null, [data, wsId])
}

export function useIsSuperAdmin() {
  const { data } = useMe()
  return data?.user.systemRole === 'SUPER_ADMIN'
}

/** Kiểm tra quyền trong workspace hiện tại. SUPER_ADMIN luôn true (khớp @perm phía BE). */
export function useCan() {
  const ws = useCurrentWorkspace()
  const superAdmin = useIsSuperAdmin()
  return React.useCallback(
    (perm: Permission) => superAdmin || !!ws?.permissions.includes(perm),
    [ws, superAdmin],
  )
}

/** Ẩn children nếu thiếu quyền. Chỉ là UX — BE vẫn là nơi kiểm tra quyền thật. */
export function Can({ perm, superAdmin, children, fallback = null }: { perm?: Permission; superAdmin?: boolean; children: React.ReactNode; fallback?: React.ReactNode }) {
  const can = useCan()
  const isSa = useIsSuperAdmin()
  const ok = superAdmin ? isSa : perm ? can(perm) : true
  return <>{ok ? children : fallback}</>
}

import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cleanParams, download, request, requestPage } from '@/api/client'
import type { AdminDashboard, AuditLog, PageQuery, Permission, PermissionDef, RoleDef, SystemRole, UserDto, WorkspaceRole } from '@/api/types'
import { meKey } from '@/features/auth/api'

// ───── Người dùng ─────
export interface UserQuery extends PageQuery {
  status?: string
  systemRole?: string
}
export function useAdminUsers(q: UserQuery) {
  return useQuery({
    queryKey: ['admin', 'users', q],
    queryFn: () => requestPage<UserDto>({ url: '/admin/users', params: cleanParams({ ...q }) }),
    placeholderData: keepPreviousData,
  })
}

export interface UserInput {
  username?: string
  fullName: string
  email?: string | null
  phone?: string | null
  password?: string
  systemRole: SystemRole
}

export function useSaveUser() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, ...body }: UserInput & { id?: number }) =>
      id
        ? request<UserDto>({ url: `/admin/users/${id}`, method: 'PUT', data: body })
        : request<UserDto>({ url: '/admin/users', method: 'POST', data: body }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'users'] }),
  })
}

export function useSetUserStatus() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status }: { id: number; status: 'ACTIVE' | 'LOCKED' }) =>
      request<UserDto>({ url: `/admin/users/${id}/status`, method: 'PATCH', data: { status } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['admin', 'users'] }),
  })
}

export function useResetPassword() {
  return useMutation({
    mutationFn: ({ id, newPassword }: { id: number; newPassword: string }) =>
      request<null>({ url: `/admin/users/${id}/reset-password`, method: 'POST', data: { newPassword } }),
  })
}

// ───── Ma trận quyền ─────
export function usePermissionDefs() {
  return useQuery({ queryKey: ['admin', 'permissions'], queryFn: () => request<PermissionDef[]>({ url: '/admin/permissions' }), staleTime: Infinity })
}
export function useRoles() {
  return useQuery({ queryKey: ['admin', 'roles'], queryFn: () => request<RoleDef[]>({ url: '/admin/roles' }) })
}
export function useSaveRolePermissions() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ code, permissions }: { code: WorkspaceRole; permissions: Permission[] }) =>
      request<RoleDef>({ url: `/admin/roles/${code}/permissions`, method: 'PUT', data: { permissions } }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'roles'] })
      qc.invalidateQueries({ queryKey: meKey })
    },
  })
}

// ───── Nhật ký ─────
export interface AuditQuery extends PageQuery {
  actor?: string
  actionType?: string
  entityType?: string
  from?: string
  to?: string
}
export function useAuditLogs(q: AuditQuery) {
  return useQuery({
    queryKey: ['audit', q],
    queryFn: () => requestPage<AuditLog>({ url: '/audit-logs', params: cleanParams({ ...q }) }),
    placeholderData: keepPreviousData,
  })
}
export function exportAuditLogs(q: AuditQuery) {
  return download('/audit-logs/export', cleanParams({ ...q, page: undefined, size: undefined }), `nhat-ky-${Date.now()}.xlsx`)
}

// ───── Dashboard Super Admin (SRS 3.1.1, 3.1.4, 3.1.6, 3.1.7) ─────
export function useAdminDashboard(periodDays = 7) {
  return useQuery({
    queryKey: ['admin', 'dashboard', periodDays],
    queryFn: () => request<AdminDashboard>({ url: '/admin/dashboard', params: { periodDays } }),
  })
}
export function downloadTopWorkspaces() {
  return download('/admin/dashboard/top-workspaces/export', {}, `top-workspace-${new Date().toISOString().slice(0, 10)}.xlsx`)
}

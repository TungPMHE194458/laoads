import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cleanParams, request, requestPage } from '@/api/client'
import type { Member, PageQuery, Workspace, WorkspaceRole, WorkspaceUpdateInput } from '@/api/types'
import { meKey } from '@/features/auth/api'

export function useWorkspace(id: number | null) {
  return useQuery({
    queryKey: ['workspace', id],
    queryFn: () => request<Workspace>({ url: `/workspaces/${id}` }),
    enabled: id != null && Number.isFinite(id),
  })
}

export function useUpdateWorkspace(id: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: WorkspaceUpdateInput) => request<Workspace>({ url: `/workspaces/${id}`, method: 'PUT', data: body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['workspace', id] })
      qc.invalidateQueries({ queryKey: meKey })
      qc.invalidateQueries({ queryKey: ['admin', 'workspaces'] })
    },
  })
}

// ───── Thành viên ─────
export interface MemberQuery extends PageQuery {
  role?: WorkspaceRole | ''
  status?: string
}

export function useMembers(wsId: number | null, q: MemberQuery) {
  return useQuery({
    queryKey: ['members', wsId, q],
    queryFn: () => requestPage<Member>({ url: `/workspaces/${wsId}/members`, params: cleanParams({ ...q }) }),
    enabled: wsId != null,
    placeholderData: keepPreviousData,
  })
}

function useMemberInvalidate(wsId: number | null) {
  const qc = useQueryClient()
  return () => {
    qc.invalidateQueries({ queryKey: ['members', wsId] })
    qc.invalidateQueries({ queryKey: ['workspace', wsId] })
  }
}

export function useAddMember(wsId: number) {
  const inv = useMemberInvalidate(wsId)
  return useMutation({
    mutationFn: (body: { identifier: string; role: WorkspaceRole }) =>
      request<Member>({ url: `/workspaces/${wsId}/members`, method: 'POST', data: body }),
    onSuccess: inv,
  })
}

export function useChangeMemberRole(wsId: number) {
  const inv = useMemberInvalidate(wsId)
  return useMutation({
    mutationFn: ({ memberId, role }: { memberId: number; role: WorkspaceRole }) =>
      request<Member>({ url: `/workspaces/${wsId}/members/${memberId}/role`, method: 'PUT', data: { role } }),
    onSuccess: inv,
  })
}

export function useRemoveMember(wsId: number) {
  const inv = useMemberInvalidate(wsId)
  return useMutation({
    mutationFn: (memberId: number) => request<null>({ url: `/workspaces/${wsId}/members/${memberId}`, method: 'DELETE' }),
    onSuccess: inv,
  })
}

// ───── Quản trị workspace (SUPER_ADMIN) ─────
export function useAdminWorkspaces(q: PageQuery & { status?: string }) {
  return useQuery({
    queryKey: ['admin', 'workspaces', q],
    queryFn: () => requestPage<Workspace>({ url: '/admin/workspaces', params: cleanParams({ ...q }) }),
    placeholderData: keepPreviousData,
  })
}

export function useCreateWorkspace() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: { code: string; name: string; description?: string; ownerUsername: string }) =>
      request<Workspace>({ url: '/admin/workspaces', method: 'POST', data: body }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'workspaces'] })
      qc.invalidateQueries({ queryKey: meKey })
    },
  })
}

export function useSetWorkspaceStatus() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status }: { id: number; status: 'ACTIVE' | 'LOCKED' }) =>
      request<Workspace>({ url: `/admin/workspaces/${id}/status`, method: 'PATCH', data: { status } }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['admin', 'workspaces'] })
      qc.invalidateQueries({ queryKey: meKey })
    },
  })
}
